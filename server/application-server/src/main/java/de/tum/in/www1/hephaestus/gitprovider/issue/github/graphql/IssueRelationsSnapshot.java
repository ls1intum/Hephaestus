package de.tum.in.www1.hephaestus.gitprovider.issue.github.graphql;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public record IssueRelationsSnapshot(
    IssueReference target,
    Optional<IssueReference> parent,
    IssueRelationsConnection subIssues,
    SubIssueSummary subIssuesSummary,
    IssueRelationsConnection tracks,
    long trackedIssuesOpen,
    long trackedIssuesClosed,
    long trackedIssuesTotal,
    IssueRelationsConnection trackedBy,
    RateLimitInfo rateLimit
) {

    public record IssueReference(
        String id,
        Long databaseId,
        int number,
        String title,
        String state,
        String stateReason,
        URI url,
        String repositoryOwner,
        String repositoryName
    ) {}

    public record IssueRelationsConnection(
        List<IssueReference> nodes,
        PageInfo pageInfo,
        long totalCount
    ) {}

    public record PageInfo(
        boolean hasNextPage,
        String endCursor
    ) {}

    public record SubIssueSummary(
        int total,
        int completed,
        double percentCompleted
    ) {}

    public record RateLimitInfo(
        int cost,
        int limit,
        long nodeCount,
        int remaining,
        int used,
        OffsetDateTime resetAt
    ) {}
}
