package de.tum.cit.aet.hephaestus.integration.gitlab.feedback;

import static de.tum.cit.aet.hephaestus.integration.gitlab.feedback.GitlabMrResolver.GRAPHQL_TIMEOUT;

import de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.gitlab.feedback.GitlabMrResolver.MrCoordinates;
import de.tum.cit.aet.hephaestus.integration.gitlab.feedback.GitlabMrResolver.MrInfo;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
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
 * <p>Gated on {@code hephaestus.gitlab.enabled=true} to track
 * {@link GitLabGraphQlClientProvider}.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
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

        MrCoordinates mr = GitlabMrResolver.parseSubjectExternalId(target.subjectExternalId());
        MrInfo info = mrResolver.resolve(scopeId, mr.projectPath(), mr.iid());
        String body = escapeSlashCommands(content.body());

        ClientGraphQlResponse response = gitLabProvider
            .forScope(scopeId)
            .documentName("CreateMergeRequestNote")
            .variable("noteableId", info.globalId())
            .variable("body", body)
            .execute()
            .block(GRAPHQL_TIMEOUT);

        if (response == null) {
            throw new FeedbackDeliveryException("Null response from CreateMergeRequestNote mutation");
        }

        List<String> mutationErrors = response.field("createNote.errors").getValue();
        if (mutationErrors != null && !mutationErrors.isEmpty()) {
            throw new FeedbackDeliveryException("GitLab createNote failed: " + mutationErrors);
        }

        String noteId = response.field("createNote.note.id").getValue();
        if (noteId == null) {
            throw new FeedbackDeliveryException("No note ID in CreateMergeRequestNote response");
        }
        log.info("Posted GitLab MR note: workspaceId={}, mrGid={}, noteId={}", scopeId, info.globalId(), noteId);
        return new SummaryHandle(noteId);
    }

    static String escapeSlashCommands(String body) {
        if (body == null || body.isEmpty()) {
            return body;
        }
        return GITLAB_SLASH_COMMAND.matcher(body).replaceAll("`$1`");
    }
}
