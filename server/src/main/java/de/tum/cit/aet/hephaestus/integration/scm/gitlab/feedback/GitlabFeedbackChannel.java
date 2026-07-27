package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.GRAPHQL_TIMEOUT;

import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.graphql.GitLabPageInfo;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.MrCoordinates;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.stereotype.Component;

/**
 * GitLab adapter for {@link FeedbackChannel}. Posts a single MR-level note via the
 * {@code CreateMergeRequestNote} GraphQL mutation.
 *
 * <p>{@link FeedbackChannel.FeedbackTarget#subjectExternalId} convention for GitLab is
 * {@code "project/full/path!iid"}; the channel resolves the MR global gid via
 * {@link GitlabMrResolver} before issuing the mutation.
 *
 * <p>Defensively backtick-escapes GitLab slash commands ({@code /approve}, {@code /merge},
 * {@code /close}, etc.) so untrusted content can't accidentally execute an action. The
 * agent layer already sanitises in {@code PullRequestCommentPoster.sanitize} before
 * calling here — this is belt-and-suspenders for callers that might bypass that path.
 *
 * <p>Gated on {@code hephaestus.integration.gitlab.enabled=true} to track
 * {@link GitLabGraphQlClientProvider}.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.gitlab.enabled", havingValue = "true", matchIfMissing = false)
public class GitlabFeedbackChannel implements FeedbackChannel {

    private static final Logger log = LoggerFactory.getLogger(GitlabFeedbackChannel.class);

    /**
     * Matches GitLab slash commands at line start. Backtick-escaped so they render as
     * inline code rather than being executed. Mirrors
     * {@code PullRequestCommentPoster.GITLAB_SLASH_COMMAND} — duplicated rather than
     * shared because the agent layer is the wrong module to hold a GitLab-specific
     * helper, and this adapter must be safe on its own.
     */
    static final Pattern GITLAB_SLASH_COMMAND = Pattern.compile(
        "^(\\s*/(?:approve|merge|close|reopen|assign|unassign|label|unlabel|lock|unlock|" +
            "milestone|estimate|spend|award|subscribe|unsubscribe|todo|done|wip|draft|ready|" +
            "due|remove_due_date|weight|epic|copy_metadata|move|confidential|shrug|tableflip)\\b)",
        Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private final GitLabGraphQlClientProvider gitLabProvider;
    private final GitlabMrResolver mrResolver;

    public GitlabFeedbackChannel(GitLabGraphQlClientProvider gitLabProvider, GitlabMrResolver mrResolver) {
        this.gitLabProvider = gitLabProvider;
        this.mrResolver = mrResolver;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public String formatPullRequestSubjectId(String repoFullName, int prNumber) {
        if (repoFullName == null || repoFullName.isBlank()) {
            throw new IllegalArgumentException("repoFullName is required");
        }
        return repoFullName + "!" + prNumber;
    }

    @Override
    public SummaryHandle postSummary(FeedbackTarget target, FeedbackContent content) {
        long scopeId = target.ref().workspaceId();
        if (gitLabProvider.isRateLimitCritical(scopeId)) {
            throw new FeedbackDeliveryException(
                "GitLab rate limit critical — skipping summary post for scope " + scopeId
            );
        }

        // The subject is a merge request ("path!iid") or an issue ("path#iid"); both post via the same
        // generic createNote mutation — only the noteable gid resolution differs.
        String subject = target.subjectExternalId();
        String noteableGid;
        if (subject != null && subject.lastIndexOf('#') > subject.lastIndexOf('!')) {
            MrCoordinates issue = GitlabMrResolver.parseIssueSubjectExternalId(subject);
            noteableGid = mrResolver.resolveIssueGid(scopeId, issue.projectPath(), issue.iid());
        } else {
            MrCoordinates mr = GitlabMrResolver.parseSubjectExternalId(subject);
            noteableGid = mrResolver.resolve(scopeId, mr.projectPath(), mr.iid()).globalId();
        }
        String body = escapeSlashCommands(content.body());

        ClientGraphQlResponse response;
        try {
            response = gitLabProvider
                .forScope(scopeId)
                .documentName("CreateMergeRequestNote")
                .variable("noteableId", noteableGid)
                .variable("body", body)
                .execute()
                .block(GRAPHQL_TIMEOUT);
        } catch (RuntimeException e) {
            // A transport/timeout error must surface as the channel's typed exception (consistent with
            // updateSummary) so PullRequestCommentPoster's catch(FeedbackDeliveryException) wraps it uniformly.
            throw new FeedbackDeliveryException("createNote transport error: " + e.getMessage(), e);
        }

        if (response == null) {
            throw new FeedbackDeliveryException("Null response from createNote mutation");
        }

        // Surface TOP-LEVEL GraphQL errors with their real reason — createNote returns no payload at all when
        // the instance is read-only ("You cannot perform write operations on a read-only instance"), the gid is
        // unresolvable, or permission is denied. Without this the caller only saw a generic "No note ID",
        // which hid a transient read-only window behind what looked like a hard failure.
        List<String> topLevelErrors = response
            .getErrors()
            .stream()
            .map(e -> e.getMessage())
            .filter(Objects::nonNull)
            .toList();
        if (!topLevelErrors.isEmpty()) {
            throw new FeedbackDeliveryException("GitLab createNote failed: " + topLevelErrors);
        }

        List<String> mutationErrors = response.field("createNote.errors").getValue();
        if (mutationErrors != null && !mutationErrors.isEmpty()) {
            throw new FeedbackDeliveryException("GitLab createNote failed: " + mutationErrors);
        }

        String noteId = response.field("createNote.note.id").getValue();
        if (noteId == null) {
            throw new FeedbackDeliveryException("No note ID in createNote response");
        }
        log.info("Posted GitLab note: workspaceId={}, noteableGid={}, noteId={}", scopeId, noteableGid, noteId);
        return new SummaryHandle(noteId);
    }

    /**
     * Edit an existing MR/issue note in place via the {@code updateNote} mutation (ADR 0021 re-review UX).
     * No noteable resolution is needed — the note's own global id ({@code externalId}, e.g.
     * {@code gid://gitlab/Note/123}) addresses it directly. Returns a typed {@link UpdateOutcome}: a
     * confirmed-deleted note is {@code GONE} (the caller re-posts); a rate-limit / transport / unknown error
     * is {@code TRANSIENT} (the caller keeps the prior summary and does NOT re-post, so a flaky update never
     * double-posts). Only a blank external id — a data bug — throws {@link FeedbackDeliveryException}.
     */
    @Override
    public UpdateOutcome updateSummary(FeedbackTarget target, String externalId, FeedbackContent content) {
        long scopeId = target.ref().workspaceId();
        // A blank id is a ledger/data bug, never recoverable by re-posting — keep it a hard error.
        if (externalId == null || externalId.isBlank()) {
            throw new FeedbackDeliveryException("Cannot edit a GitLab note in place: external note id is missing");
        }
        // Rate-limit / network / unknown errors are TRANSIENT: keep the prior summary, do NOT re-post (a flaky
        // update must not double-post a second summary). Only a confirmed-deleted note is GONE.
        if (gitLabProvider.isRateLimitCritical(scopeId)) {
            return UpdateOutcome.transientFailure("GitLab rate limit critical for scope " + scopeId);
        }
        String body = escapeSlashCommands(content.body());

        ClientGraphQlResponse response;
        try {
            response = gitLabProvider
                .forScope(scopeId)
                .documentName("UpdateNote")
                .variable("id", externalId)
                .variable("body", body)
                .execute()
                .block(GRAPHQL_TIMEOUT);
        } catch (RuntimeException e) {
            return UpdateOutcome.transientFailure("updateNote transport error: " + e.getMessage());
        }

        if (response == null) {
            return UpdateOutcome.transientFailure("Null response from updateNote mutation");
        }

        // A DELETED note surfaces as a TOP-LEVEL GraphQL error (the global id resolves to nothing), NOT a
        // mutation-payload error — GitLab returns no `updateNote` object at all. Without this check the
        // deleted-note case falls through to the no-id branch below and is mis-read as TRANSIENT, so an
        // orphaned summary (e.g. after a mirror re-import wipes the old note) never gets re-posted.
        List<String> topLevelErrors = response
            .getErrors()
            .stream()
            .map(e -> e.getMessage())
            .filter(Objects::nonNull)
            .toList();
        if (!topLevelErrors.isEmpty()) {
            return looksGone(topLevelErrors)
                ? UpdateOutcome.gone("GitLab updateNote (top-level): " + topLevelErrors)
                : UpdateOutcome.transientFailure("GitLab updateNote top-level errors: " + topLevelErrors);
        }

        List<String> mutationErrors = response.field("updateNote.errors").getValue();
        if (mutationErrors != null && !mutationErrors.isEmpty()) {
            return looksGone(mutationErrors)
                ? UpdateOutcome.gone("GitLab updateNote: " + mutationErrors)
                : UpdateOutcome.transientFailure("GitLab updateNote failed: " + mutationErrors);
        }

        String noteId = response.field("updateNote.note.id").getValue();
        if (noteId == null) {
            // The mutation neither confirmed gone nor returned an id — treat as transient, don't double-post.
            return UpdateOutcome.transientFailure("No note id in updateNote response");
        }
        log.info("Edited GitLab note in place: workspaceId={}, noteId={}", scopeId, noteId);
        return UpdateOutcome.edited(new SummaryHandle(noteId));
    }

    /** Page size per request for {@link #findExistingSummary}'s marker scan — GitLab caps {@code first} at 100. */
    private static final int EXISTING_SUMMARY_SEARCH_PAGE_SIZE = 100;

    /**
     * Bounded page budget for {@link #findExistingSummary}: 3 pages of
     * {@link #EXISTING_SUMMARY_SEARCH_PAGE_SIZE} discussions covers the MRs this bot comments on without an
     * unbounded scan; past it, {@code UNKNOWN} is the correct answer rather than a guess at absence.
     */
    private static final int EXISTING_SUMMARY_SEARCH_PAGE_BUDGET = 3;

    /**
     * Dedup lookup for the delivery-recovery crash window: pages this MR's discussions through the same
     * {@code GetMergeRequestDiscussions} document {@link GitlabInlineFindingChannel} and discussion sync use,
     * and returns the marker-bearing note's own id — the handle {@link #updateSummary} edits in place.
     *
     * <p>Only {@code ABSENT} is a green light to post; {@code UNKNOWN} leaves the delivery {@code PENDING} for a
     * later attempt. Every branch that cannot prove the marker is missing therefore answers {@code UNKNOWN}.
     *
     * <p>The document nests {@code notes(first: 100)} inside each discussion with no cursor of its own, so a
     * thread whose {@code notes.pageInfo.hasNextPage} is true holds notes no cursor can reach — after seeing one,
     * exhausting the outer connection still answers {@code UNKNOWN}.
     */
    @Override
    public ExistingSummaryLookup findExistingSummary(FeedbackTarget target, String marker) {
        if (marker == null || marker.isBlank()) {
            return ExistingSummaryLookup.unknown();
        }
        long scopeId = target.ref().workspaceId();
        if (gitLabProvider.isRateLimitCritical(scopeId)) {
            return ExistingSummaryLookup.unknown();
        }
        String subject = target.subjectExternalId();
        if (subject != null && subject.lastIndexOf('#') > subject.lastIndexOf('!')) {
            // Issue subject: this document is merge-request-only and issue notes have no listing counterpart.
            return ExistingSummaryLookup.unknown();
        }
        MrCoordinates mr = GitlabMrResolver.parseSubjectExternalId(subject);

        boolean sawTruncatedThread = false;
        String cursor = null;
        for (int page = 0; page < EXISTING_SUMMARY_SEARCH_PAGE_BUDGET; page++) {
            try {
                ClientGraphQlResponse response = gitLabProvider
                    .forScope(scopeId)
                    .documentName("GetMergeRequestDiscussions")
                    .variable("fullPath", mr.projectPath())
                    .variable("iid", String.valueOf(mr.iid()))
                    .variable("first", EXISTING_SUMMARY_SEARCH_PAGE_SIZE)
                    .variable("after", cursor)
                    .execute()
                    .block(GRAPHQL_TIMEOUT);
                if (response == null || !response.getErrors().isEmpty()) {
                    return ExistingSummaryLookup.unknown();
                }

                List<Map<String, Object>> discussions = response
                    .field("project.mergeRequest.discussions.nodes")
                    .getValue();
                if (discussions == null) {
                    return ExistingSummaryLookup.unknown();
                }
                for (Map<String, Object> discussion : discussions) {
                    Map<String, Object> notes = notesConnection(discussion);
                    for (Map<String, Object> note : nodesOf(notes)) {
                        String noteId = (String) note.get("id");
                        String body = (String) note.get("body");
                        if (noteId != null && body != null && body.contains(marker)) {
                            return ExistingSummaryLookup.found(new SummaryHandle(noteId));
                        }
                    }
                    sawTruncatedThread = sawTruncatedThread || hasUnreadNotes(notes);
                }

                GitLabPageInfo pageInfo = response
                    .field("project.mergeRequest.discussions.pageInfo")
                    .toEntity(GitLabPageInfo.class);
                if (pageInfo == null) {
                    return ExistingSummaryLookup.unknown();
                }
                if (!pageInfo.hasNextPage()) {
                    return sawTruncatedThread ? ExistingSummaryLookup.unknown() : ExistingSummaryLookup.absent();
                }
                cursor = pageInfo.endCursor();
                if (cursor == null || cursor.isBlank()) {
                    return ExistingSummaryLookup.unknown();
                }
            } catch (RuntimeException e) {
                log.debug(
                    "Existing-summary dedup lookup failed (treated as unknown, not absent): scopeId={}, error={}",
                    scopeId,
                    e.getMessage()
                );
                return ExistingSummaryLookup.unknown();
            }
        }
        return ExistingSummaryLookup.unknown();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> notesConnection(Map<String, Object> discussion) {
        Object notes = discussion.get("notes");
        return notes instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> nodesOf(Map<String, Object> connection) {
        Object nodes = connection.get("nodes");
        return nodes instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static boolean hasUnreadNotes(Map<String, Object> notesConnection) {
        Object pageInfo = notesConnection.get("pageInfo");
        return pageInfo instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("hasNextPage"));
    }

    /** Conservative NOT_FOUND heuristic: GitLab signals a deleted note only via a free-text mutation error. */
    private static boolean looksGone(List<String> errors) {
        return errors
            .stream()
            .filter(Objects::nonNull)
            .map(e -> e.toLowerCase(Locale.ROOT))
            .anyMatch(
                e ->
                    e.contains("not found") ||
                    e.contains("does not exist") ||
                    e.contains("could not be found") ||
                    e.contains("couldn't be found")
            );
    }

    static String escapeSlashCommands(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        return GITLAB_SLASH_COMMAND.matcher(body).replaceAll("`$1`");
    }
}
