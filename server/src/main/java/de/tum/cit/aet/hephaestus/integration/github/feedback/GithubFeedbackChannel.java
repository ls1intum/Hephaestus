package de.tum.cit.aet.hephaestus.integration.github.feedback;

import static de.tum.cit.aet.hephaestus.integration.github.feedback.GithubPrNodeIdResolver.GRAPHQL_TIMEOUT;

import de.tum.cit.aet.hephaestus.integration.github.common.GitHubGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackChannel;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackDeliveryException;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.stereotype.Component;

/**
 * GitHub adapter for {@link FeedbackChannel}. Posts a single PR-level comment via
 * the {@code AddPullRequestComment} GraphQL mutation.
 *
 * <p>{@link FeedbackChannel.FeedbackTarget#subjectExternalId} convention for GitHub is
 * {@code "owner/repo#prNumber"} — the channel parses, resolves the PR node ID
 * via {@link GithubPrNodeIdResolver}, then issues the mutation. The returned
 * {@link FeedbackChannel.SummaryHandle} carries the comment node ID so
 * {@code FeedbackPostService} can edit in place on subsequent runs.
 */
@Component
public class GithubFeedbackChannel implements FeedbackChannel {

    private static final Logger log = LoggerFactory.getLogger(GithubFeedbackChannel.class);

    private final GitHubGraphQlClientProvider gitHubProvider;
    private final GithubPrNodeIdResolver prNodeIdResolver;

    public GithubFeedbackChannel(GitHubGraphQlClientProvider gitHubProvider, GithubPrNodeIdResolver prNodeIdResolver) {
        this.gitHubProvider = gitHubProvider;
        this.prNodeIdResolver = prNodeIdResolver;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public String formatPullRequestSubjectId(String repoFullName, int prNumber) {
        if (repoFullName == null || repoFullName.isBlank()) {
            throw new IllegalArgumentException("repoFullName is required");
        }
        if (repoFullName.split("/", 3).length != 2) {
            throw new IllegalArgumentException("GitHub repoFullName must be 'owner/repo': " + repoFullName);
        }
        return repoFullName + "#" + prNumber;
    }

    @Override
    public SummaryHandle postSummary(FeedbackTarget target, FeedbackContent content) {
        long scopeId = target.ref().workspaceId();
        if (gitHubProvider.isRateLimitCritical(scopeId)) {
            throw new FeedbackDeliveryException(
                "GitHub rate limit critical — skipping summary post for scope " + scopeId
            );
        }

        PrCoordinates pr = parseSubjectExternalId(target.subjectExternalId());
        String prNodeId = prNodeIdResolver.resolve(scopeId, pr.owner(), pr.name(), pr.number());
        String commentNodeId = createComment(scopeId, prNodeId, content.body());
        log.info(
            "Posted GitHub PR comment: workspaceId={}, prNodeId={}, commentId={}",
            scopeId,
            prNodeId,
            commentNodeId
        );
        return new SummaryHandle(commentNodeId);
    }

    private String createComment(long scopeId, String subjectId, String body) {
        ClientGraphQlResponse response = gitHubProvider
            .forScope(scopeId)
            .documentName("AddPullRequestComment")
            .variable("subjectId", subjectId)
            .variable("body", body)
            .execute()
            .block(GRAPHQL_TIMEOUT);

        if (response == null) {
            throw new FeedbackDeliveryException("Null response from AddPullRequestComment mutation");
        }
        gitHubProvider.trackRateLimit(scopeId, response);

        if (response.getErrors() != null && !response.getErrors().isEmpty()) {
            throw new FeedbackDeliveryException("GitHub addComment failed: " + response.getErrors());
        }

        String commentNodeId = response.field("addComment.commentEdge.node.id").getValue();
        if (commentNodeId == null) {
            throw new FeedbackDeliveryException("No comment ID in AddPullRequestComment response");
        }
        return commentNodeId;
    }

    /**
     * Splits {@code "owner/repo#42"} (GitHub PR external-id convention used by the
     * agent layer) into the three components needed by the GraphQL query.
     */
    static PrCoordinates parseSubjectExternalId(String subjectExternalId) {
        if (subjectExternalId == null || subjectExternalId.isBlank()) {
            throw new FeedbackDeliveryException("subjectExternalId is required for GitHub PR feedback");
        }
        int hashIdx = subjectExternalId.lastIndexOf('#');
        if (hashIdx <= 0 || hashIdx == subjectExternalId.length() - 1) {
            throw new FeedbackDeliveryException(
                "Invalid GitHub PR subjectExternalId (expected owner/repo#number): " + subjectExternalId
            );
        }
        String repoFullName = subjectExternalId.substring(0, hashIdx);
        String numberPart = subjectExternalId.substring(hashIdx + 1);
        String[] parts = repoFullName.split("/", 2);
        if (parts.length != 2) {
            throw new FeedbackDeliveryException(
                "Invalid GitHub PR subjectExternalId (expected owner/repo#number): " + subjectExternalId
            );
        }
        int number;
        try {
            number = Integer.parseInt(numberPart);
        } catch (NumberFormatException e) {
            throw new FeedbackDeliveryException(
                "Invalid GitHub PR subjectExternalId — number must be integer: " + subjectExternalId
            );
        }
        return new PrCoordinates(parts[0], parts[1], number);
    }

    record PrCoordinates(String owner, String name, int number) {}
}
