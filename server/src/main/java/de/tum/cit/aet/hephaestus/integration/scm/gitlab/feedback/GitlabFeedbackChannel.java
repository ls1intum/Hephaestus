package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.GRAPHQL_TIMEOUT;

import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.MrCoordinates;
import java.util.List;
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

        ClientGraphQlResponse response = gitLabProvider
            .forScope(scopeId)
            .documentName("CreateMergeRequestNote")
            .variable("noteableId", noteableGid)
            .variable("body", body)
            .execute()
            .block(GRAPHQL_TIMEOUT);

        if (response == null) {
            throw new FeedbackDeliveryException("Null response from createNote mutation");
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
     * {@code gid://gitlab/Note/123}) addresses it directly. A vendor rejection (the prior note was deleted
     * by a human, the id is stale) surfaces as {@link FeedbackDeliveryException}; the caller then re-posts.
     */
    @Override
    public SummaryHandle updateSummary(FeedbackTarget target, String externalId, FeedbackContent content) {
        long scopeId = target.ref().workspaceId();
        if (gitLabProvider.isRateLimitCritical(scopeId)) {
            throw new FeedbackDeliveryException(
                "GitLab rate limit critical — skipping summary edit for scope " + scopeId
            );
        }
        if (externalId == null || externalId.isBlank()) {
            throw new FeedbackDeliveryException("Cannot edit a GitLab note in place: external note id is missing");
        }
        String body = escapeSlashCommands(content.body());

        ClientGraphQlResponse response = gitLabProvider
            .forScope(scopeId)
            .documentName("UpdateNote")
            .variable("id", externalId)
            .variable("body", body)
            .execute()
            .block(GRAPHQL_TIMEOUT);

        if (response == null) {
            throw new FeedbackDeliveryException("Null response from updateNote mutation");
        }

        List<String> mutationErrors = response.field("updateNote.errors").getValue();
        if (mutationErrors != null && !mutationErrors.isEmpty()) {
            throw new FeedbackDeliveryException("GitLab updateNote failed: " + mutationErrors);
        }

        String noteId = response.field("updateNote.note.id").getValue();
        if (noteId == null) {
            throw new FeedbackDeliveryException("No note ID in updateNote response");
        }
        log.info("Edited GitLab note in place: workspaceId={}, noteId={}", scopeId, noteId);
        return new SummaryHandle(noteId);
    }

    static String escapeSlashCommands(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        return GITLAB_SLASH_COMMAND.matcher(body).replaceAll("`$1`");
    }
}
