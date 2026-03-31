package de.tum.in.www1.hephaestus.agent.handler;

import static de.tum.in.www1.hephaestus.agent.handler.PullRequestCommentPoster.GRAPHQL_TIMEOUT;

import com.fasterxml.jackson.databind.JsonNode;
import de.tum.in.www1.hephaestus.agent.handler.PracticeDetectionResultParser.DiffNote;
import de.tum.in.www1.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.lang.Nullable;

/**
 * Posts inline diff notes on GitHub PRs (via pull request review threads) or GitLab MRs
 * (via individual diff note mutations).
 *
 * <p><b>GitHub:</b> Uses {@code addPullRequestReview} with {@code threads} for atomic, single-notification
 * delivery. All diff notes are posted as part of a single review with {@code COMMENT} event (neutral).
 *
 * <p><b>GitLab:</b> No batch API exists, so each diff note is posted individually via
 * {@code createDiffNote}. Best-effort: continues on per-note failure, stops on rate limit.
 *
 * <p>Package-private — created as {@code @Bean} in {@link JobTypeHandlerConfiguration}.
 */
class DiffNotePoster {

    private static final Logger log = LoggerFactory.getLogger(DiffNotePoster.class);

    private final PullRequestCommentPoster commentPoster;
    private final GitHubGraphQlClientProvider gitHubProvider;

    @Nullable
    private final GitLabGraphQlClientProvider gitLabProvider;

    private final WorkspaceRepository workspaceRepository;

    DiffNotePoster(
        PullRequestCommentPoster commentPoster,
        GitHubGraphQlClientProvider gitHubProvider,
        @Nullable GitLabGraphQlClientProvider gitLabProvider,
        WorkspaceRepository workspaceRepository
    ) {
        this.commentPoster = commentPoster;
        this.gitHubProvider = gitHubProvider;
        this.gitLabProvider = gitLabProvider;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Posts diff notes for the given job. Routes to GitHub or GitLab based on workspace provider.
     *
     * @param job       the completed agent job (must have metadata with repository info)
     * @param diffNotes the sanitized diff notes to post
     * @return result with posted/failed counts
     */
    DiffNoteResult postDiffNotes(AgentJob job, List<DiffNote> diffNotes) {
        if (diffNotes.isEmpty()) {
            return new DiffNoteResult(0, 0);
        }

        Long workspaceId = job.getWorkspace().getId();
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new JobDeliveryException("Workspace not found: id=" + workspaceId));

        if (workspace.getProviderType() == GitProviderType.GITHUB) {
            return postGitHubDiffNotes(workspaceId, job, diffNotes);
        } else {
            return postGitLabDiffNotes(workspaceId, job, diffNotes);
        }
    }

    // ── GitHub: atomic review with threads ──

    private DiffNoteResult postGitHubDiffNotes(Long scopeId, AgentJob job, List<DiffNote> diffNotes) {
        if (gitHubProvider.isRateLimitCritical(scopeId)) {
            log.warn("GitHub rate limit critical — skipping diff notes: jobId={}", job.getId());
            return new DiffNoteResult(0, diffNotes.size());
        }

        JsonNode metadata = job.getMetadata();
        String repoFullName = PullRequestCommentPoster.requireMetadataText(metadata, "repository_full_name");
        int prNumber = PullRequestCommentPoster.requireMetadataInt(metadata, "pr_number");
        String commitSha = PullRequestCommentPoster.requireMetadataText(metadata, "commit_sha");

        String[] parts = repoFullName.split("/", 2);
        if (parts.length != 2) {
            throw new JobDeliveryException("Invalid repository_full_name: " + repoFullName);
        }

        // Resolve PR node ID via injected comment poster (reuses same GraphQL query)
        String prNodeId = commentPoster.resolveGitHubPrNodeId(scopeId, parts[0], parts[1], prNumber);

        // Build threads array for the review
        List<Map<String, Object>> threads = new ArrayList<>();
        for (DiffNote note : diffNotes) {
            String sanitizedBody = PullRequestCommentPoster.sanitize(note.body());
            if (sanitizedBody.isBlank()) {
                continue;
            }

            Map<String, Object> thread = new HashMap<>();
            thread.put("path", note.filePath());
            thread.put("body", sanitizedBody);

            // GitHub review threads: single-line vs multi-line annotation
            boolean isMultiLine = note.endLine() != null && note.endLine() > note.startLine();
            if (isMultiLine) {
                // Multi-line: startLine..line on the RIGHT (new) side
                thread.put("startLine", note.startLine());
                thread.put("line", note.endLine());
                thread.put("side", "RIGHT");
                thread.put("startSide", "RIGHT");
            } else {
                // Single-line: annotate the new version of the file
                thread.put("line", note.startLine());
                thread.put("side", "RIGHT");
            }

            threads.add(thread);
        }

        if (threads.isEmpty()) {
            log.debug("All diff notes were empty after sanitization: jobId={}", job.getId());
            return new DiffNoteResult(0, 0);
        }

        // Single atomic mutation — all-or-nothing
        try {
            ClientGraphQlResponse response = gitHubProvider
                .forScope(scopeId)
                .documentName("AddPullRequestReviewWithThreads")
                .variable("pullRequestId", prNodeId)
                .variable("event", "COMMENT")
                .variable("commitOID", commitSha)
                .variable("threads", threads)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response == null) {
                throw new JobDeliveryException("Null response from AddPullRequestReviewWithThreads");
            }
            gitHubProvider.trackRateLimit(scopeId, response);

            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                log.warn(
                    "GitHub addPullRequestReview with threads failed: jobId={}, errors={}, threadCount={}",
                    job.getId(),
                    response.getErrors(),
                    threads.size()
                );
                return new DiffNoteResult(0, threads.size());
            }

            log.info(
                "Posted {} GitHub diff notes as review: jobId={}, prNodeId={}",
                threads.size(),
                job.getId(),
                prNodeId
            );
            return new DiffNoteResult(threads.size(), 0);
        } catch (JobDeliveryException e) {
            throw e;
        } catch (Exception e) {
            log.warn("GitHub diff notes failed: jobId={}, threadCount={}", job.getId(), threads.size(), e);
            return new DiffNoteResult(0, threads.size());
        }
    }

    // ── GitLab: individual diff notes ──

    private DiffNoteResult postGitLabDiffNotes(Long scopeId, AgentJob job, List<DiffNote> diffNotes) {
        if (gitLabProvider == null) {
            throw new JobDeliveryException(
                "GitLab provider not configured — cannot post diff notes for scope " + scopeId
            );
        }
        if (gitLabProvider.isRateLimitCritical(scopeId)) {
            log.warn("GitLab rate limit critical — skipping diff notes: jobId={}", job.getId());
            return new DiffNoteResult(0, diffNotes.size());
        }

        JsonNode metadata = job.getMetadata();
        String repoFullName = PullRequestCommentPoster.requireMetadataText(metadata, "repository_full_name");
        int prNumber = PullRequestCommentPoster.requireMetadataInt(metadata, "pr_number");

        // Resolve MR global ID and diffRefs
        MrInfo mrInfo = resolveGitLabMrInfo(scopeId, repoFullName, prNumber);
        if (mrInfo.headSha == null || mrInfo.startSha == null) {
            log.warn(
                "GitLab MR missing diffRefs — skipping diff notes: jobId={}, mrGlobalId={}",
                job.getId(),
                mrInfo.globalId
            );
            return new DiffNoteResult(0, diffNotes.size());
        }

        int posted = 0;
        int failed = 0;
        int remaining = diffNotes.size();

        for (DiffNote note : diffNotes) {
            remaining--;
            String sanitizedBody = PullRequestCommentPoster.sanitize(note.body());
            if (sanitizedBody.isBlank()) {
                continue;
            }

            try {
                // Build DiffPositionInput
                Map<String, Object> position = new HashMap<>();
                position.put("headSha", mrInfo.headSha);
                position.put("startSha", mrInfo.startSha);
                position.put("baseSha", mrInfo.baseSha);
                Map<String, String> paths = new HashMap<>();
                paths.put("newPath", note.filePath());
                position.put("paths", paths);
                position.put("newLine", note.startLine());

                ClientGraphQlResponse response = gitLabProvider
                    .forScope(scopeId)
                    .documentName("CreateDiffNote")
                    .variable("noteableId", mrInfo.globalId)
                    .variable("body", sanitizedBody)
                    .variable("position", position)
                    .execute()
                    .block(GRAPHQL_TIMEOUT);

                if (response == null) {
                    failed++;
                    log.warn("Null response posting GitLab diff note: jobId={}, file={}", job.getId(), note.filePath());
                    continue;
                }

                List<String> errors = response.field("createDiffNote.errors").getValue();
                if (errors != null && !errors.isEmpty()) {
                    // Fallback: if line is outside diff hunk, post as regular MR comment
                    if (isLineCodeError(errors)) {
                        log.info(
                            "Diff note line outside diff hunk, falling back to MR comment: jobId={}, file={}, line={}",
                            job.getId(),
                            note.filePath(),
                            note.startLine()
                        );
                        if (postFallbackComment(scopeId, mrInfo.globalId, note, sanitizedBody, job)) {
                            posted++;
                        } else {
                            failed++;
                        }
                        continue;
                    }
                    failed++;
                    log.warn(
                        "GitLab createDiffNote failed: jobId={}, file={}, line={}, errors={}",
                        job.getId(),
                        note.filePath(),
                        note.startLine(),
                        errors
                    );
                    continue;
                }

                posted++;
            } catch (Exception e) {
                // Check for rate limit — stop processing remaining notes
                if (isRateLimitError(e)) {
                    log.warn("GitLab rate limit hit during diff note posting — stopping: jobId={}", job.getId());
                    failed += remaining + 1; // current note + unprocessed remaining
                    break;
                }
                failed++;
                log.warn(
                    "GitLab diff note failed: jobId={}, file={}, line={}",
                    job.getId(),
                    note.filePath(),
                    note.startLine(),
                    e
                );
            }
        }

        log.info("Posted {} GitLab diff notes ({} failed): jobId={}", posted, failed, job.getId());
        return new DiffNoteResult(posted, failed);
    }

    private MrInfo resolveGitLabMrInfo(Long scopeId, String projectPath, int mrIid) {
        ClientGraphQlResponse response = gitLabProvider
            .forScope(scopeId)
            .documentName("GetMergeRequestGlobalId")
            .variable("fullPath", projectPath)
            .variable("iid", String.valueOf(mrIid))
            .execute()
            .block(GRAPHQL_TIMEOUT);

        if (response == null) {
            throw new JobDeliveryException("Null response resolving MR info: " + projectPath + "!" + mrIid);
        }

        String globalId = response.field("project.mergeRequest.id").getValue();
        if (globalId == null) {
            List<?> errors = response.getErrors();
            throw new JobDeliveryException(
                "MR not found via GraphQL: " +
                    projectPath +
                    "!" +
                    mrIid +
                    (errors.isEmpty() ? "" : ", errors=" + errors)
            );
        }

        // diffRefs may be null if the MR has no diffs yet
        String baseSha = response.field("project.mergeRequest.diffRefs.baseSha").getValue();
        String headSha = response.field("project.mergeRequest.diffRefs.headSha").getValue();
        String startSha = response.field("project.mergeRequest.diffRefs.startSha").getValue();

        return new MrInfo(globalId, baseSha, headSha, startSha);
    }

    private static boolean isRateLimitError(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("rate limit") || message.contains("429"));
    }

    private static boolean isLineCodeError(List<String> errors) {
        return errors.stream().anyMatch(e ->
            e.toLowerCase().contains("line code") || e.toLowerCase().contains("line_code")
        );
    }

    /**
     * Fallback: post a diff note as a regular MR comment when the line is outside the diff hunk.
     * Includes file path and line number in the comment body for context.
     */
    private boolean postFallbackComment(
        Long scopeId,
        String mrGlobalId,
        DiffNote note,
        String sanitizedBody,
        AgentJob job
    ) {
        try {
            String fallbackBody = String.format(
                "**`%s:%d`**\n\n%s",
                note.filePath(),
                note.startLine(),
                sanitizedBody
            );
            ClientGraphQlResponse response = gitLabProvider
                .forScope(scopeId)
                .documentName("CreateMergeRequestNote")
                .variable("noteableId", mrGlobalId)
                .variable("body", fallbackBody)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response == null) {
                log.warn("Null response posting fallback MR comment: jobId={}", job.getId());
                return false;
            }

            List<String> errors = response.field("createNote.errors").getValue();
            if (errors != null && !errors.isEmpty()) {
                log.warn("Fallback MR comment failed: jobId={}, errors={}", job.getId(), errors);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Fallback MR comment failed: jobId={}, file={}", job.getId(), note.filePath(), e);
            return false;
        }
    }

    record MrInfo(String globalId, @Nullable String baseSha, @Nullable String headSha, @Nullable String startSha) {}

    record DiffNoteResult(int posted, int failed) {}
}
