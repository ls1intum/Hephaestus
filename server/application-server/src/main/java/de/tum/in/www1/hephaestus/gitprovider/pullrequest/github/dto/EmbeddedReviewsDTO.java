package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto;

import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PullRequestReviewConnection;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.github.dto.GitHubPullRequestReviewEventDTO.GitHubReviewDTO;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * DTO for embedded reviews fetched inline with pull requests.
 * <p>
 * Contains the reviews fetched in the initial PR query plus pagination info
 * to determine if additional API calls are needed.
 */
public record EmbeddedReviewsDTO(
    List<GitHubReviewDTO> reviews,
    int totalCount,
    boolean hasNextPage,
    @Nullable String endCursor
) {
    /**
     * Creates an EmbeddedReviewsDTO from a GraphQL PullRequestReviewConnection.
     *
     * @param connection the GraphQL connection (may be null)
     * @return EmbeddedReviewsDTO or empty DTO if connection is null
     */
    public static EmbeddedReviewsDTO fromConnection(@Nullable PullRequestReviewConnection connection) {
        if (connection == null) {
            return empty();
        }

        List<GitHubReviewDTO> reviews = connection.getNodes() != null
            ? connection
                  .getNodes()
                  .stream()
                  .map(GitHubReviewDTO::fromPullRequestReview)
                  .filter(Objects::nonNull)
                  .toList()
            : Collections.emptyList();

        boolean hasNextPage =
            connection.getPageInfo() != null && Boolean.TRUE.equals(connection.getPageInfo().getHasNextPage());

        String endCursor = connection.getPageInfo() != null ? connection.getPageInfo().getEndCursor() : null;

        return new EmbeddedReviewsDTO(reviews, connection.getTotalCount(), hasNextPage, endCursor);
    }

    /**
     * Returns an empty EmbeddedReviewsDTO.
     */
    public static EmbeddedReviewsDTO empty() {
        return new EmbeddedReviewsDTO(Collections.emptyList(), 0, false, null);
    }

    /**
     * Checks if there are more reviews to fetch beyond what's embedded.
     */
    public boolean needsPagination() {
        return hasNextPage;
    }
}
