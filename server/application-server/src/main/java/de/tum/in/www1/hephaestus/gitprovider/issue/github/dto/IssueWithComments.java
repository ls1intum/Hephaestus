package de.tum.in.www1.hephaestus.gitprovider.issue.github.dto;

import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssue;
import org.springframework.lang.Nullable;

/**
 * Container for an Issue DTO and its embedded comments and project items.
 * <p>
 * Used during sync to process comments and project items inline with issues, eliminating N+1 queries.
 * <p>
 * <h2>Embedded Data</h2>
 * <ul>
 *   <li>{@code embeddedComments} - First 10 comments (pagination for issues with more)</li>
 *   <li>{@code embeddedProjectItems} - First 5 project items (pagination for issues in more projects)</li>
 * </ul>
 */
public record IssueWithComments(
    GitHubIssueDTO issue,
    EmbeddedCommentsDTO embeddedComments,
    EmbeddedProjectItemsDTO embeddedProjectItems
) {
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

        String context = "Issue #" + ghIssue.getNumber();

        GitHubIssueDTO dto = GitHubIssueDTO.fromIssue(ghIssue);
        EmbeddedCommentsDTO comments = EmbeddedCommentsDTO.fromConnection(ghIssue.getComments(), context);
        EmbeddedProjectItemsDTO projectItems = EmbeddedProjectItemsDTO.fromConnection(
            ghIssue.getProjectItems(),
            context
        );

        return new IssueWithComments(dto, comments, projectItems);
    }
}
