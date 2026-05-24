package de.tum.cit.aet.hephaestus.integration.github.feedback;

import de.tum.cit.aet.hephaestus.integration.spi.ApprovalChannel;

import static de.tum.cit.aet.hephaestus.integration.github.feedback.GithubPrNodeIdResolver.GRAPHQL_TIMEOUT;

import de.tum.cit.aet.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.github.feedback.GithubFeedbackChannel.PrCoordinates;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.stereotype.Component;

/**
 * GitHub adapter for {@link ApprovalChannel}. Submits an APPROVE pull-request review
 * via the {@code ApprovePullRequest} GraphQL mutation (a {@code addPullRequestReview}
 * specialised to {@code event: APPROVE}).
 *
 * <p>Not yet exercised by the agent layer — present so {@code IntegrationFrameworkBootstrap}
 * can validate the manifest's {@code APPROVAL_WORKFLOW} capability has a wired bean.
 */
@Component
public class GithubApprovalChannel implements ApprovalChannel {

    private static final Logger log = LoggerFactory.getLogger(GithubApprovalChannel.class);

    private final GitHubGraphQlClientProvider gitHubProvider;
    private final GithubPrNodeIdResolver prNodeIdResolver;

    public GithubApprovalChannel(
        GitHubGraphQlClientProvider gitHubProvider,
        GithubPrNodeIdResolver prNodeIdResolver
    ) {
        this.gitHubProvider = gitHubProvider;
        this.prNodeIdResolver = prNodeIdResolver;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public void approve(FeedbackChannel.FeedbackTarget target, String message) {
        long scopeId = target.ref().workspaceId();
        if (gitHubProvider.isRateLimitCritical(scopeId)) {
            throw new FeedbackDeliveryException(
                "GitHub rate limit critical — skipping approval for scope " + scopeId
            );
        }

        PrCoordinates pr = GithubFeedbackChannel.parseSubjectExternalId(target.subjectExternalId());
        String prNodeId = prNodeIdResolver.resolve(scopeId, pr.owner(), pr.name(), pr.number());

        ClientGraphQlResponse response = gitHubProvider
            .forScope(scopeId)
            .documentName("ApprovePullRequest")
            .variable("pullRequestId", prNodeId)
            .variable("body", message)
            .execute()
            .block(GRAPHQL_TIMEOUT);

        if (response == null) {
            throw new FeedbackDeliveryException("Null response from ApprovePullRequest mutation");
        }
        gitHubProvider.trackRateLimit(scopeId, response);

        if (response.getErrors() != null && !response.getErrors().isEmpty()) {
            throw new FeedbackDeliveryException("GitHub ApprovePullRequest failed: " + response.getErrors());
        }

        String reviewId = response.field("addPullRequestReview.pullRequestReview.id").getValue();
        log.info("Posted GitHub approval review: workspaceId={}, prNodeId={}, reviewId={}",
            scopeId, prNodeId, reviewId);
    }
}
