package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto;

import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequest;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.dto.EmbeddedProjectItemsDTO;
import org.springframework.lang.Nullable;

/**
 * Container for a Pull Request DTO with its embedded reviews, review threads, and project items.
 * <p>
 * Used during sync to process reviews, review comments, and project items inline with PRs,
 * eliminating N+1 queries.
 * <p>
 * <h2>Embedded Data</h2>
 * <ul>
 *   <li>{@code embeddedReviews} - First 5 reviews (pagination for PRs with more)</li>
 *   <li>{@code embeddedReviewThreads} - First 5 review threads with their comments</li>
 *   <li>{@code embeddedProjectItems} - First 5 project items (pagination for PRs in more projects)</li>
 * </ul>
 */
public record PullRequestWithReviewThreads(
    GitHubPullRequestDTO pullRequest,
    EmbeddedReviewsDTO embeddedReviews,
    EmbeddedReviewThreadsDTO embeddedReviewThreads,
    EmbeddedProjectItemsDTO embeddedProjectItems
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
        EmbeddedProjectItemsDTO projectItems = EmbeddedProjectItemsDTO.fromConnection(ghPullRequest.getProjectItems());

        return new PullRequestWithReviewThreads(dto, reviews, threads, projectItems);
    }
}
