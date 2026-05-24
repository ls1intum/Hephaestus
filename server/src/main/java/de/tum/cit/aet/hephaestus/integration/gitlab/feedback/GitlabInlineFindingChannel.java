package de.tum.cit.aet.hephaestus.integration.gitlab.feedback;

import static de.tum.cit.aet.hephaestus.integration.gitlab.feedback.GitlabMrResolver.GRAPHQL_TIMEOUT;

import de.tum.cit.aet.hephaestus.gitprovider.common.gitlab.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.gitlab.feedback.GitlabMrResolver.MrCoordinates;
import de.tum.cit.aet.hephaestus.integration.gitlab.feedback.GitlabMrResolver.MrInfo;
import de.tum.cit.aet.hephaestus.integration.scm.feedback.ScmInlineFindingChannel;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.spi.FindingAnchor;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.stereotype.Component;

/**
 * GitLab adapter for {@link ScmInlineFindingChannel}. Posts inline diff notes one at a
 * time via {@code CreateDiffNote} (GitLab has no batch API). For positions outside the
 * diff hunk, falls back to a regular MR comment with {@code file:line} prefix.
 *
 * <p>Before posting, deletes existing hephaestus-marked diff notes on the MR
 * ({@code GetMergeRequestNotes} + {@code DestroyNote}) so re-runs don't accumulate
 * duplicates. Dedup is best-effort — failure is logged but doesn't block fresh posts.
 *
 * <p>Non-{@link FindingAnchor.DiffAnchor} anchors are counted as failed.
 *
 * <p>Gated on {@code hephaestus.gitlab.enabled=true} to track
 * {@link GitLabGraphQlClientProvider}.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitlabInlineFindingChannel implements ScmInlineFindingChannel {

    private static final Logger log = LoggerFactory.getLogger(GitlabInlineFindingChannel.class);

    /** GitLab's max per-page limit for note pagination — sufficient since we post at most ~30 notes per review. */
    private static final int NOTES_PAGE_SIZE = 500;

    private final GitLabGraphQlClientProvider gitLabProvider;
    private final GitlabMrResolver mrResolver;

    public GitlabInlineFindingChannel(GitLabGraphQlClientProvider gitLabProvider, GitlabMrResolver mrResolver) {
        this.gitLabProvider = gitLabProvider;
        this.mrResolver = mrResolver;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public InlineResult postInlineFindings(FeedbackChannel.FeedbackTarget target, List<InlineFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return new InlineResult(0, 0);
        }
        long scopeId = target.ref().workspaceId();
        if (gitLabProvider.isRateLimitCritical(scopeId)) {
            log.warn("GitLab rate limit critical — skipping {} inline findings: workspaceId={}",
                findings.size(), scopeId);
            return new InlineResult(0, findings.size());
        }

        MrCoordinates mr = GitlabMrResolver.parseSubjectExternalId(target.subjectExternalId());
        MrInfo mrInfo = mrResolver.resolve(scopeId, mr.projectPath(), mr.iid());
        if (mrInfo.headSha() == null || mrInfo.startSha() == null) {
            log.warn(
                "GitLab MR missing diffRefs — skipping diff notes: workspaceId={}, mrGid={}",
                scopeId,
                mrInfo.globalId()
            );
            return new InlineResult(0, findings.size());
        }

        // Dedup old marker-bearing notes — best effort.
        deleteOldMarkedNotes(scopeId, mr.projectPath(), mr.iid(), markerFor(findings));

        int posted = 0;
        int failed = 0;
        int remaining = findings.size();

        for (InlineFinding finding : findings) {
            remaining--;
            if (!(finding.anchor() instanceof FindingAnchor.DiffAnchor diff)) {
                log.warn("Skipping non-diff anchor on GitLab inline finding: anchor={}", finding.anchor());
                failed++;
                continue;
            }
            if (finding.body() == null || finding.body().isBlank()) {
                continue;
            }

            String body = appendMarker(GitlabFeedbackChannel.escapeSlashCommands(finding.body()), finding.marker());

            try {
                Map<String, Object> position = buildPosition(diff, mrInfo);

                ClientGraphQlResponse response = gitLabProvider
                    .forScope(scopeId)
                    .documentName("CreateDiffNote")
                    .variable("noteableId", mrInfo.globalId())
                    .variable("body", body)
                    .variable("position", position)
                    .execute()
                    .block(GRAPHQL_TIMEOUT);

                if (response == null) {
                    failed++;
                    log.warn(
                        "Null response posting GitLab diff note: workspaceId={}, file={}",
                        scopeId,
                        diff.filePath()
                    );
                    continue;
                }

                List<String> errors = response.field("createDiffNote.errors").getValue();
                if (errors != null && !errors.isEmpty()) {
                    // Fallback: position outside diff hunk — post as regular MR comment instead
                    if (isLineCodeError(errors)) {
                        log.info(
                            "Diff note line outside diff hunk, falling back to MR comment: workspaceId={}, file={}, line={}",
                            scopeId,
                            diff.filePath(),
                            diff.newLineNumber()
                        );
                        if (postFallbackComment(scopeId, mrInfo.globalId(), diff, finding.body(), finding.marker())) {
                            posted++;
                        } else {
                            failed++;
                        }
                        continue;
                    }
                    failed++;
                    log.warn(
                        "GitLab createDiffNote failed: workspaceId={}, file={}, line={}, errors={}",
                        scopeId,
                        diff.filePath(),
                        diff.newLineNumber(),
                        errors
                    );
                    continue;
                }

                posted++;
            } catch (Exception e) {
                if (isRateLimitError(e)) {
                    log.warn("GitLab rate limit hit during diff note posting — stopping: workspaceId={}", scopeId);
                    failed += remaining + 1;
                    break;
                }
                failed++;
                log.warn(
                    "GitLab diff note failed: workspaceId={}, file={}, line={}",
                    scopeId,
                    diff.filePath(),
                    diff.newLineNumber(),
                    e
                );
            }
        }

        log.info("Posted {} GitLab inline findings ({} failed): workspaceId={}", posted, failed, scopeId);
        return new InlineResult(posted, failed);
    }

    private static Map<String, Object> buildPosition(FindingAnchor.DiffAnchor diff, MrInfo mrInfo) {
        Map<String, Object> position = new HashMap<>();
        position.put("headSha", mrInfo.headSha());
        position.put("startSha", mrInfo.startSha());
        position.put("baseSha", mrInfo.baseSha());
        // oldPath required by GitLab to match the note position to the diff file in the
        // Changes tab. Correct for new + modified files. For renamed files oldPath
        // should be the pre-rename path, but the DiffAnchor only carries the new path.
        // Renames are rare in student assignments; if needed, resolve from MR diff metadata.
        Map<String, String> paths = new HashMap<>();
        paths.put("newPath", diff.filePath());
        paths.put("oldPath", diff.filePath());
        position.put("paths", paths);
        position.put("newLine", diff.newLineNumber());
        return position;
    }

    private void deleteOldMarkedNotes(long scopeId, String projectPath, int mrIid, String marker) {
        if (marker == null || marker.isBlank()) {
            return;
        }
        try {
            ClientGraphQlResponse response = gitLabProvider
                .forScope(scopeId)
                .documentName("GetMergeRequestNotes")
                .variable("fullPath", projectPath)
                .variable("iid", String.valueOf(mrIid))
                .variable("first", NOTES_PAGE_SIZE)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response == null) {
                return;
            }

            List<Map<String, Object>> notes = response.field("project.mergeRequest.notes.nodes").getValue();
            if (notes == null || notes.isEmpty()) {
                return;
            }

            int deleted = 0;
            for (Map<String, Object> note : notes) {
                String body = (String) note.get("body");
                String noteId = (String) note.get("id");
                Boolean isSystem = (Boolean) note.get("system");

                if (Boolean.TRUE.equals(isSystem) || noteId == null || body == null) {
                    continue;
                }
                if (!body.contains(marker)) {
                    continue;
                }

                try {
                    ClientGraphQlResponse deleteResponse = gitLabProvider
                        .forScope(scopeId)
                        .documentName("DestroyNote")
                        .variable("noteId", noteId)
                        .execute()
                        .block(GRAPHQL_TIMEOUT);

                    if (deleteResponse != null) {
                        List<String> errors = deleteResponse.field("destroyNote.errors").getValue();
                        if (errors == null || errors.isEmpty()) {
                            deleted++;
                        } else {
                            log.debug("Failed to delete old diff note: noteId={}, errors={}", noteId, errors);
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to delete old diff note: noteId={}", noteId, e);
                }
            }

            if (deleted > 0) {
                log.info(
                    "Deleted {} old marked diff notes before re-posting: workspaceId={}, mr={}!{}",
                    deleted,
                    scopeId,
                    projectPath,
                    mrIid
                );
            }
        } catch (Exception e) {
            log.debug("Failed to query existing MR notes for dedup: workspaceId={}", scopeId, e);
        }
    }

    private boolean postFallbackComment(
        long scopeId,
        String mrGlobalId,
        FindingAnchor.DiffAnchor diff,
        String body,
        String marker
    ) {
        try {
            String fallbackBody = appendMarker(
                String.format(
                    "**`%s:%d`**%n%n%s",
                    diff.filePath(),
                    diff.newLineNumber(),
                    GitlabFeedbackChannel.escapeSlashCommands(body)
                ),
                marker
            );
            ClientGraphQlResponse response = gitLabProvider
                .forScope(scopeId)
                .documentName("CreateMergeRequestNote")
                .variable("noteableId", mrGlobalId)
                .variable("body", fallbackBody)
                .execute()
                .block(GRAPHQL_TIMEOUT);

            if (response == null) {
                log.warn("Null response posting fallback MR comment: workspaceId={}", scopeId);
                return false;
            }

            List<String> errors = response.field("createNote.errors").getValue();
            if (errors != null && !errors.isEmpty()) {
                log.warn("Fallback MR comment failed: workspaceId={}, errors={}", scopeId, errors);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Fallback MR comment failed: workspaceId={}, file={}", scopeId, diff.filePath(), e);
            return false;
        }
    }

    /** Returns the marker shared by all findings in the batch (they originate from one parser pass). */
    private static String markerFor(List<InlineFinding> findings) {
        return findings.isEmpty() ? null : findings.get(0).marker();
    }

    private static String appendMarker(String body, String marker) {
        if (marker == null || marker.isBlank()) {
            return body;
        }
        return body + "\n" + marker;
    }

    private static boolean isRateLimitError(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("rate limit") || message.contains("429"));
    }

    private static boolean isLineCodeError(List<String> errors) {
        return errors
            .stream()
            .anyMatch(e -> e.toLowerCase().contains("line code") || e.toLowerCase().contains("line_code"));
    }
}
