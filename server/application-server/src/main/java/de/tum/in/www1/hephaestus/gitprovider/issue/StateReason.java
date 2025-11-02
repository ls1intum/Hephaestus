package de.tum.in.www1.hephaestus.gitprovider.issue;

/**
 * Represents the reason an issue or pull request was closed or reopened.
 * Matches GitHub GraphQL IssueStateReason enum.
 * @see <a href="https://docs.github.com/en/graphql/reference/enums#issuestatereason">GitHub GraphQL IssueStateReason</a>
 */
public enum StateReason {
    /** The issue/PR was completed or merged. */
    COMPLETED,
    /** The issue/PR was closed without being completed. */
    NOT_PLANNED,
    /** The issue/PR was reopened. */
    REOPENED,
}
