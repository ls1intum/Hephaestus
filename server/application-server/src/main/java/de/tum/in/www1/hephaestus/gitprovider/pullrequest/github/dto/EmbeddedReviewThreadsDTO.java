package de.tum.in.www1.hephaestus.gitprovider.pullrequest.github.dto;

import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPullRequestReviewThreadConnection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * DTO for embedded review threads fetched inline with pull requests.
 * <p>
 * Contains the review threads fetched in the initial PR query plus pagination info
 * to determine if additional API calls are needed.
 * <p>
 * Each thread contains comments (first 10), which may also need pagination.
 */
public record EmbeddedReviewThreadsDTO(
    List<GitHubReviewThreadDTO> threads,
    int totalCount,
    boolean hasNextPage,
    @Nullable String endCursor
) {
    /**
     * Creates an EmbeddedReviewThreadsDTO from a GraphQL GHPullRequestReviewThreadConnection.
     *
     * @param connection the GraphQL connection (may be null)
     * @return EmbeddedReviewThreadsDTO or empty DTO if connection is null
     */
    public static EmbeddedReviewThreadsDTO fromConnection(@Nullable GHPullRequestReviewThreadConnection connection) {
        if (connection == null) {
            return empty();
        }

        List<GitHubReviewThreadDTO> threads = connection.getNodes() != null
            ? connection
                .getNodes()
                .stream()
                .map(GitHubReviewThreadDTO::fromReviewThread)
                .filter(Objects::nonNull)
                .toList()
            : Collections.emptyList();

        boolean hasNextPage =
            connection.getPageInfo() != null && Boolean.TRUE.equals(connection.getPageInfo().getHasNextPage());

        String endCursor = connection.getPageInfo() != null ? connection.getPageInfo().getEndCursor() : null;

        return new EmbeddedReviewThreadsDTO(threads, connection.getTotalCount(), hasNextPage, endCursor);
    }

    /**
     * Returns an empty EmbeddedReviewThreadsDTO.
     */
    public static EmbeddedReviewThreadsDTO empty() {
        return new EmbeddedReviewThreadsDTO(Collections.emptyList(), 0, false, null);
    }

    /**
     * Checks if there are more threads to fetch beyond what's embedded.
     */
    public boolean needsPagination() {
        return hasNextPage;
    }
}
