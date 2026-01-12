package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto;

import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequest;
import org.springframework.lang.Nullable;

/**
 * Container for a PR DTO and its embedded reviews.
 * <p>
 * Used during sync to process reviews inline with PRs, eliminating N+1 queries.
 */
public record PullRequestWithReviews(GitHubPullRequestDTO pullRequest, EmbeddedReviewsDTO embeddedReviews) {
    /**
     * Creates a PullRequestWithReviews from a GraphQL GHPullRequest model.
     *
     * @param pr the GraphQL GHPullRequest (may be null)
     * @return PullRequestWithReviews or null if pr is null
     */
    @Nullable
    public static PullRequestWithReviews fromPullRequest(@Nullable GHPullRequest pr) {
        if (pr == null) {
            return null;
        }

        GitHubPullRequestDTO dto = GitHubPullRequestDTO.fromPullRequest(pr);
        EmbeddedReviewsDTO reviews = EmbeddedReviewsDTO.fromConnection(pr.getReviews());

        return new PullRequestWithReviews(dto, reviews);
    }
}
