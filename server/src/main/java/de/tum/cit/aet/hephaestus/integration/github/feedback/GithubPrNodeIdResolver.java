package de.tum.cit.aet.hephaestus.integration.github.feedback;

import de.tum.cit.aet.hephaestus.integration.github.common.GitHubGraphQlClientProvider;
import de.tum.cit.aet.hephaestus.integration.spi.FeedbackDeliveryException;
import java.time.Duration;
import java.util.List;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.stereotype.Component;

/**
 * Resolves a GitHub pull request's node ID via the {@code GetPullRequestNodeId}
 * GraphQL query. Shared by the three GitHub feedback adapters
 * ({@link GithubFeedbackChannel}, {@link GithubInlineFindingChannel},
 * {@link GithubApprovalChannel}) — all of them need the node ID before issuing the
 * corresponding mutation, and centralising the lookup keeps mutation timeouts +
 * error-shape handling in one place.
 *
 * <p>Throws {@link FeedbackDeliveryException} on any failure so callers can mark
 * delivery as failed with a clear reason.
 */
@Component
class GithubPrNodeIdResolver {

    static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(15);

    private final GitHubGraphQlClientProvider gitHubProvider;

    GithubPrNodeIdResolver(GitHubGraphQlClientProvider gitHubProvider) {
        this.gitHubProvider = gitHubProvider;
    }

    String resolve(long scopeId, String owner, String name, int number) {
        ClientGraphQlResponse response = gitHubProvider
            .forScope(scopeId)
            .documentName("GetPullRequestNodeId")
            .variable("owner", owner)
            .variable("name", name)
            .variable("number", number)
            .execute()
            .block(GRAPHQL_TIMEOUT);

        if (response == null) {
            throw new FeedbackDeliveryException(
                "Null response resolving PR node ID: " + owner + "/" + name + "#" + number
            );
        }
        gitHubProvider.trackRateLimit(scopeId, response);

        String nodeId = response.field("repository.pullRequest.id").getValue();
        if (nodeId == null) {
            List<?> errors = response.getErrors();
            throw new FeedbackDeliveryException(
                "PR not found via GraphQL: " +
                    owner +
                    "/" +
                    name +
                    "#" +
                    number +
                    (errors.isEmpty() ? "" : ", errors=" + errors)
            );
        }
        return nodeId;
    }
}
