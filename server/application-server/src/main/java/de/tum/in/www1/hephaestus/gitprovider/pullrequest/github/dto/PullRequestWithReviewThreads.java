package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto;

import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequest;
import org.springframework.lang.Nullable;

/**
 * Container for a Pull Request DTO with its embedded reviews and review threads.
 * <p>
 * Used during sync to process reviews and review comments inline with PRs,
 * eliminating N+1 queries.
 */
public record PullRequestWithReviewThreads(
    GitHubPullRequestDTO pullRequest,
    EmbeddedReviewsDTO embeddedReviews,
    EmbeddedReviewThreadsDTO embeddedReviewThreads
) {
    /**
     * Creates a PullRequestWithReviewThreads from a GraphQL GHPullRequest model.
     *
     * @param ghPullRequest the GraphQL GHPullRequest (may be null)
     * @return PullRequestWithReviewThreads or null if ghPullRequest is null
     */
    @Nullable
    public static PullRequestWithReviewThreads fromPullRequest(@Nullable GHPullRequest ghPullRequest) {
        if (ghPullRequest == null) {
            return null;
        }

        GitHubPullRequestDTO dto = GitHubPullRequestDTO.fromPullRequest(ghPullRequest);
        EmbeddedReviewsDTO reviews = EmbeddedReviewsDTO.fromConnection(ghPullRequest.getReviews());
        EmbeddedReviewThreadsDTO threads = EmbeddedReviewThreadsDTO.fromConnection(ghPullRequest.getReviewThreads());

        return new PullRequestWithReviewThreads(dto, reviews, threads);
    }
}
