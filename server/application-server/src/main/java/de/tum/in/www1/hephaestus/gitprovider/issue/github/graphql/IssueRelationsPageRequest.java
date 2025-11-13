package de.tum.in.www1.hephaestus.gitprovider.issue.github.graphql;

import java.util.Optional;

public record IssueRelationsPageRequest(
    Integer subIssuesPageSize,
    String subIssuesCursor,
    Integer trackedPageSize,
    String trackedCursor,
    Integer trackedInPageSize,
    String trackedInCursor
) {

    public static IssueRelationsPageRequest defaults() {
        return new IssueRelationsPageRequest(null, null, null, null, null, null);
    }

    public Optional<String> subIssuesCursorOptional() {
        return Optional.ofNullable(subIssuesCursor);
    }

    public Optional<String> trackedCursorOptional() {
        return Optional.ofNullable(trackedCursor);
    }

    public Optional<String> trackedInCursorOptional() {
        return Optional.ofNullable(trackedInCursor);
    }
}
