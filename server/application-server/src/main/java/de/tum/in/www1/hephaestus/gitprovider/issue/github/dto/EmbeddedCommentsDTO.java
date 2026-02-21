package de.tum.in.www1.hephaestus.gitprovider.issue.github.dto;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GraphQlConnectionOverflowDetector;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueCommentConnection;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.github.dto.GitHubIssueCommentEventDTO.GitHubCommentDTO;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.springframework.lang.Nullable;

/**
 * DTO for embedded comments fetched inline with issues.
 * <p>
 * Contains the comments fetched in the initial issue query plus pagination info
 * to determine if additional API calls are needed.
 */
public record EmbeddedCommentsDTO(
    List<GitHubCommentDTO> comments,
    int totalCount,
    boolean hasNextPage,
    @Nullable String endCursor
) {
    /**
     * Creates an EmbeddedCommentsDTO from a GraphQL GHIssueCommentConnection.
     *
     * @param connection the GraphQL connection (may be null)
     * @param context    contextual description for overflow logging (e.g. "Issue #42 in owner/repo")
     * @return EmbeddedCommentsDTO or empty DTO if connection is null
     */
    public static EmbeddedCommentsDTO fromConnection(@Nullable GHIssueCommentConnection connection, String context) {
        if (connection == null) {
            return empty();
        }

        List<GitHubCommentDTO> comments =
            connection.getNodes() != null
                ? connection
                      .getNodes()
                      .stream()
                      .map(GitHubCommentDTO::fromIssueComment)
                      .filter(Objects::nonNull)
                      .toList()
                : Collections.emptyList();

        boolean hasNextPage =
            connection.getPageInfo() != null && Boolean.TRUE.equals(connection.getPageInfo().getHasNextPage());

        String endCursor = connection.getPageInfo() != null ? connection.getPageInfo().getEndCursor() : null;

        GraphQlConnectionOverflowDetector.check("comments", comments.size(), connection.getTotalCount(), context);

        return new EmbeddedCommentsDTO(comments, connection.getTotalCount(), hasNextPage, endCursor);
    }

    /**
     * Returns an empty EmbeddedCommentsDTO.
     */
    public static EmbeddedCommentsDTO empty() {
        return new EmbeddedCommentsDTO(Collections.emptyList(), 0, false, null);
    }

    /**
     * Checks if there are more comments to fetch beyond what's embedded.
     */
    public boolean needsPagination() {
        return hasNextPage;
    }
}
