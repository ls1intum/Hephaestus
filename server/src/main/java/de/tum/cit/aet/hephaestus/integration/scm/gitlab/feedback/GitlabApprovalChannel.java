package de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback;

import static de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.GRAPHQL_TIMEOUT;

import de.tum.cit.aet.hephaestus.integration.core.spi.ApprovalChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.common.GitLabGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.MrCoordinates;
import de.tum.cit.aet.hephaestus.integration.scm.gitlab.feedback.GitlabMrResolver.MrInfo;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.stereotype.Component;

/**
 * GitLab adapter for {@link ApprovalChannel}: approves an MR via the
 * {@code ApproveMergeRequest} mutation. Optional message goes as a regular MR note —
 * {@code mergeRequestApprove} does not accept a body. Gated on
 * {@code hephaestus.gitlab.enabled=true} to track {@link GitLabGraphQlClientProvider}.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.gitlab", name = "enabled", havingValue = "true")
public class GitlabApprovalChannel implements ApprovalChannel {

    private static final Logger log = LoggerFactory.getLogger(GitlabApprovalChannel.class);

    private final GitLabGraphQlClientProvider gitLabProvider;
    private final GitlabMrResolver mrResolver;
    private final GitlabFeedbackChannel feedbackChannel;

    public GitlabApprovalChannel(
        GitLabGraphQlClientProvider gitLabProvider,
        GitlabMrResolver mrResolver,
        GitlabFeedbackChannel feedbackChannel
    ) {
        this.gitLabProvider = gitLabProvider;
        this.mrResolver = mrResolver;
        this.feedbackChannel = feedbackChannel;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITLAB;
    }

    @Override
    public void approve(FeedbackChannel.FeedbackTarget target, String message) {
        long scopeId = target.ref().workspaceId();
        if (gitLabProvider.isRateLimitCritical(scopeId)) {
            throw new FeedbackDeliveryException("GitLab rate limit critical — skipping approval for scope " + scopeId);
        }

        MrCoordinates mr = GitlabMrResolver.parseSubjectExternalId(target.subjectExternalId());
        MrInfo info = mrResolver.resolve(scopeId, mr.projectPath(), mr.iid());

        ClientGraphQlResponse response = gitLabProvider
            .forScope(scopeId)
            .documentName("ApproveMergeRequest")
            .variable("mergeRequestId", info.globalId())
            .execute()
            .block(GRAPHQL_TIMEOUT);

        if (response == null) {
            throw new FeedbackDeliveryException("Null response from ApproveMergeRequest mutation");
        }

        List<String> errors = response.field("mergeRequestApprove.errors").getValue();
        if (errors != null && !errors.isEmpty()) {
            throw new FeedbackDeliveryException("GitLab mergeRequestApprove failed: " + errors);
        }
        log.info("Approved GitLab MR: workspaceId={}, mrGid={}", scopeId, info.globalId());

        // GitLab approval mutation accepts no body — post message as separate note.
        if (message != null && !message.isBlank()) {
            feedbackChannel.postSummary(target, new FeedbackChannel.FeedbackContent(message, ""));
        }
    }
}
