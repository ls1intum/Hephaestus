package de.tum.in.www1.hephaestus.gitprovider.issue.github.dto;

import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssue;
import org.springframework.lang.Nullable;

/**
 * Container for an Issue DTO and its embedded comments.
 * <p>
 * Used during sync to process comments inline with issues, eliminating N+1 queries.
 */
public record IssueWithComments(GitHubIssueDTO issue, EmbeddedCommentsDTO embeddedComments) {
    /**
     * Creates an IssueWithComments from a GraphQL GHIssue model.
     *
     * @param ghIssue the GraphQL GHIssue (may be null)
     * @return IssueWithComments or null if ghIssue is null
     */
    @Nullable
    public static IssueWithComments fromIssue(@Nullable GHIssue ghIssue) {
        if (ghIssue == null) {
            return null;
        }

        GitHubIssueDTO dto = GitHubIssueDTO.fromIssue(ghIssue);
        EmbeddedCommentsDTO comments = EmbeddedCommentsDTO.fromConnection(ghIssue.getComments());

        return new IssueWithComments(dto, comments);
    }
}
