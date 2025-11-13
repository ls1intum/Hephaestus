package de.tum.in.www1.hephaestus.gitprovider.issue.github.graphql;

import de.tum.in.www1.hephaestus.gitprovider.common.github.graphql.GitHubGraphQlDocuments;
import de.tum.in.www1.hephaestus.gitprovider.common.github.graphql.GitHubGraphQlExecutor;
import de.tum.in.www1.hephaestus.gitprovider.common.github.graphql.GitHubGraphQlQuery;
import de.tum.in.www1.hephaestus.gitprovider.github.graphql.generated.types.Issue;
import de.tum.in.www1.hephaestus.gitprovider.github.graphql.generated.types.IssueConnection;
import de.tum.in.www1.hephaestus.gitprovider.github.graphql.generated.types.PageInfo;
import de.tum.in.www1.hephaestus.gitprovider.github.graphql.generated.types.RateLimit;
import de.tum.in.www1.hephaestus.gitprovider.github.graphql.generated.types.Repository;
import de.tum.in.www1.hephaestus.gitprovider.github.graphql.generated.types.RepositoryOwner;
import de.tum.in.www1.hephaestus.gitprovider.github.graphql.generated.types.SubIssuesSummary;
import de.tum.in.www1.hephaestus.gitprovider.issue.github.IssueRelationsUpdater;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.stereotype.Service;

/**
 * Fetches GitHub issue relations via GraphQL and keeps the local store in sync.
 */
@Service
public class GitHubIssueRelationsGraphQLService {

    private static final String SUB_ISSUES_FEATURE_HEADER = "GraphQL-Features";
    private static final String SUB_ISSUES_FEATURE_FLAG = "sub_issues";

    private final GitHubGraphQlExecutor executor;
    private final IssueRelationsUpdater relationsUpdater;
    private final String issueRelationsDocument;

    public GitHubIssueRelationsGraphQLService(
        GitHubGraphQlExecutor executor,
        GitHubGraphQlDocuments documents,
        IssueRelationsUpdater relationsUpdater,
        @Value("classpath:github/graphql/operations/issue/issue-relations.graphql") String issueRelationsDocumentLocation
    ) {
        this.executor = executor;
        this.relationsUpdater = relationsUpdater;
        this.issueRelationsDocument = documents.load(issueRelationsDocumentLocation);
    }

    public IssueRelationsSnapshot fetchForWorkspace(
        Long workspaceId,
        String owner,
        String repository,
        int issueNumber,
        IssueRelationsPageRequest pageRequest
    ) {
        GitHubGraphQlQuery<IssueRelationsSnapshot> query = GitHubGraphQlQuery
            .builder(issueRelationsDocument, this::toSnapshot)
            .variables(buildVariables(owner, repository, issueNumber, pageRequest))
            .headers(headers -> headers.set(SUB_ISSUES_FEATURE_HEADER, SUB_ISSUES_FEATURE_FLAG))
            .build();

        return executor.execute(workspaceId, query);
    }

    public IssueRelationsSnapshot synchronizeForWorkspace(
        Long workspaceId,
        String owner,
        String repository,
        int issueNumber,
        IssueRelationsPageRequest pageRequest
    ) {
        IssueRelationsSnapshot snapshot = fetchForWorkspace(workspaceId, owner, repository, issueNumber, pageRequest);
        relationsUpdater.applyGraphQlSnapshot(workspaceId, snapshot);
        return snapshot;
    }

    private static Map<String, Object> buildVariables(
        String owner,
        String repository,
        int issueNumber,
        IssueRelationsPageRequest pageRequest
    ) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("owner", owner);
        variables.put("name", repository);
        variables.put("number", issueNumber);

        if (pageRequest != null) {
            if (pageRequest.subIssuesPageSize() != null) {
                variables.put("subIssuesPageSize", pageRequest.subIssuesPageSize());
            }
            pageRequest.subIssuesCursorOptional().ifPresent(cursor -> variables.put("subIssuesCursor", cursor));
            if (pageRequest.trackedPageSize() != null) {
                variables.put("trackedPageSize", pageRequest.trackedPageSize());
            }
            pageRequest.trackedCursorOptional().ifPresent(cursor -> variables.put("trackedCursor", cursor));
            if (pageRequest.trackedInPageSize() != null) {
                variables.put("trackedInPageSize", pageRequest.trackedInPageSize());
            }
            pageRequest.trackedInCursorOptional().ifPresent(cursor -> variables.put("trackedInCursor", cursor));
        }

        return variables;
    }

    private IssueRelationsSnapshot toSnapshot(ClientGraphQlResponse response) {
        IssueWithCounts issuePayload = Optional
            .ofNullable(response.field("repository.issue"))
            .map(field -> field.toEntity(IssueWithCounts.class))
            .orElseThrow(() -> new IllegalStateException("Missing issue payload"));

        IssueRelationsSnapshot.IssueReference target = Optional
            .ofNullable(toReference(issuePayload))
            .orElseThrow(() -> new IllegalStateException("Missing issue reference"));

        Optional<IssueRelationsSnapshot.IssueReference> parent = Optional
            .ofNullable(issuePayload.getParent())
            .map(this::toReference);

        IssueRelationsSnapshot.IssueRelationsConnection subIssues = toConnection(issuePayload.getSubIssues());
        IssueRelationsSnapshot.SubIssueSummary subIssueSummary = toSubIssueSummary(issuePayload.getSubIssuesSummary());
        IssueRelationsSnapshot.IssueRelationsConnection tracks = toConnection(issuePayload.getTrackedIssues());
        IssueRelationsSnapshot.IssueRelationsConnection trackedBy = toConnection(issuePayload.getTrackedInIssues());

        int trackedOpen = Optional.ofNullable(issuePayload.getTrackedIssuesOpen()).orElse(0);
        int trackedClosed = Optional.ofNullable(issuePayload.getTrackedIssuesClosed()).orElse(0);
        int trackedTotal = Optional
            .ofNullable(issuePayload.getTrackedIssuesTotal())
            .orElseGet(() -> issuePayload.getTrackedIssues() != null ? issuePayload.getTrackedIssues().getTotalCount() : 0);

        RateLimit rateLimitPayload = response.field("rateLimit").toEntity(RateLimit.class);
        IssueRelationsSnapshot.RateLimitInfo rateLimitInfo = toRateLimit(rateLimitPayload);

        return new IssueRelationsSnapshot(
            target,
            parent,
            subIssues,
            subIssueSummary,
            tracks,
            trackedOpen,
            trackedClosed,
            trackedTotal,
            trackedBy,
            rateLimitInfo
        );
    }

    private IssueRelationsSnapshot.IssueRelationsConnection toConnection(IssueConnection connection) {
        if (connection == null) {
            return new IssueRelationsSnapshot.IssueRelationsConnection(List.of(), new IssueRelationsSnapshot.PageInfo(false, null), 0);
        }
        List<IssueRelationsSnapshot.IssueReference> references = Optional
            .ofNullable(connection.getNodes())
            .orElseGet(List::of)
            .stream()
            .map(this::toReference)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(ArrayList::new));

        PageInfo pageInfoPayload = connection.getPageInfo();
        IssueRelationsSnapshot.PageInfo pageInfo = pageInfoPayload == null
            ? new IssueRelationsSnapshot.PageInfo(false, null)
            : new IssueRelationsSnapshot.PageInfo(pageInfoPayload.getHasNextPage(), pageInfoPayload.getEndCursor());

        return new IssueRelationsSnapshot.IssueRelationsConnection(references, pageInfo, connection.getTotalCount());
    }

    private IssueRelationsSnapshot.SubIssueSummary toSubIssueSummary(SubIssuesSummary payload) {
        if (payload == null) {
            return new IssueRelationsSnapshot.SubIssueSummary(0, 0, 0.0d);
        }
        return new IssueRelationsSnapshot.SubIssueSummary(payload.getTotal(), payload.getCompleted(), payload.getPercentCompleted());
    }

    private IssueRelationsSnapshot.RateLimitInfo toRateLimit(RateLimit payload) {
        if (payload == null) {
            return new IssueRelationsSnapshot.RateLimitInfo(0, 0, 0, 0, 0, OffsetDateTime.MIN);
        }
        OffsetDateTime resetAt = Optional.ofNullable(payload.getResetAt()).orElse(OffsetDateTime.MIN);
        return new IssueRelationsSnapshot.RateLimitInfo(
            payload.getCost(),
            payload.getLimit(),
            payload.getNodeCount(),
            payload.getRemaining(),
            payload.getUsed(),
            resetAt
        );
    }

    private IssueRelationsSnapshot.IssueReference toReference(Issue issue) {
        if (issue == null) {
            return null;
        }
        Repository issueRepository = issue.getRepository();
        RepositoryOwner owner = issueRepository != null ? issueRepository.getOwner() : null;
        String ownerLogin = owner != null ? owner.getLogin() : null;
        String repositoryName = issueRepository != null ? issueRepository.getName() : null;
        String state = issue.getState() != null ? issue.getState().name() : null;
        String stateReason = issue.getStateReason() != null ? issue.getStateReason().name() : null;
        return new IssueRelationsSnapshot.IssueReference(
            issue.getId(),
            issue.getDatabaseId() == null ? null : issue.getDatabaseId().longValue(),
            issue.getNumber(),
            issue.getTitle(),
            state,
            stateReason,
            issue.getUrl(),
            ownerLogin,
            repositoryName
        );
    }

    private static class IssueWithCounts extends Issue {

        private Integer trackedIssuesOpen;
        private Integer trackedIssuesClosed;
        private Integer trackedIssuesTotal;

        Integer getTrackedIssuesOpen() {
            return trackedIssuesOpen;
        }

        void setTrackedIssuesOpen(Integer trackedIssuesOpen) {
            this.trackedIssuesOpen = trackedIssuesOpen;
        }

        Integer getTrackedIssuesClosed() {
            return trackedIssuesClosed;
        }

        void setTrackedIssuesClosed(Integer trackedIssuesClosed) {
            this.trackedIssuesClosed = trackedIssuesClosed;
        }

        Integer getTrackedIssuesTotal() {
            return trackedIssuesTotal;
        }

        void setTrackedIssuesTotal(Integer trackedIssuesTotal) {
            this.trackedIssuesTotal = trackedIssuesTotal;
        }
    }
}
