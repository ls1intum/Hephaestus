package de.tum.in.www1.hephaestus.gitprovider.subissue.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.IssueConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.SubIssuesSummary;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for syncing sub-issue (parent-child) relationships from GitHub.
 * <p>
 * GitHub's sub-issues feature creates a hierarchical relationship between
 * issues.
 * This service handles:
 * <ul>
 * <li>Processing webhook events to update relationships in real-time</li>
 * <li>Workspace-scoped GraphQL sync to fetch all relationships</li>
 * </ul>
 * <p>
 * <b>Architecture Notes:</b>
 * <ul>
 * <li>GraphQL calls are made OUTSIDE transactions to avoid blocking DB
 * connections</li>
 * <li>Database writes use separate transactions via REQUIRES_NEW</li>
 * <li>Uses Spring's {@code documentName()} for query loading</li>
 * <li>Respects sync cooldown to avoid excessive API calls</li>
 * </ul>
 *
 * @see GitHubSubIssuesMessageHandler
 */
@Service
public class GitHubSubIssueSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubSubIssueSyncService.class);
    private static final int GRAPHQL_PAGE_SIZE = 100;
    private static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(30);

    /** Document name for graphql-documents/GetSubIssuesForRepository.graphql */
    private static final String GET_SUB_ISSUES_DOCUMENT = "GetSubIssuesForRepository";

    private final IssueRepository issueRepository;
    private final RepositoryRepository repositoryRepository;
    private final WorkspaceRepository workspaceRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final int syncCooldownInMinutes;

    public GitHubSubIssueSyncService(
        IssueRepository issueRepository,
        RepositoryRepository repositoryRepository,
        WorkspaceRepository workspaceRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        @Value("${monitoring.sync-cooldown-in-minutes}") int syncCooldownInMinutes
    ) {
        this.issueRepository = issueRepository;
        this.repositoryRepository = repositoryRepository;
        this.workspaceRepository = workspaceRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.syncCooldownInMinutes = syncCooldownInMinutes;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WEBHOOK EVENT PROCESSING
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Process a sub-issue webhook event.
     * <p>
     * Links or unlinks the parent-child relationship based on the event type.
     * Also updates the parent's sub_issues_summary if provided.
     */
    @Transactional
    public void processSubIssueEvent(
        long subIssueId,
        long parentIssueId,
        boolean isLink,
        @Nullable SubIssuesSummaryDTO parentSummary
    ) {
        logger.info(
            "Processing sub-issue event: subIssue={}, parentIssue={}, isLink={}",
            subIssueId,
            parentIssueId,
            isLink
        );

        Optional<Issue> subIssueOpt = issueRepository.findById(subIssueId);
        if (subIssueOpt.isEmpty()) {
            logger.debug("Sub-issue {} not found in database, skipping", subIssueId);
            return;
        }

        Issue subIssue = subIssueOpt.get();
        if (isLink) {
            linkSubIssueToParent(subIssue, parentIssueId, parentSummary);
        } else {
            unlinkSubIssueFromParent(subIssue, parentIssueId, parentSummary);
        }
    }

    /** Convenience overload for tests and cases where summary is not available. */
    @Transactional
    public void processSubIssueEvent(long subIssueId, long parentIssueId, boolean isLink) {
        processSubIssueEvent(subIssueId, parentIssueId, isLink, null);
    }

    private void linkSubIssueToParent(Issue subIssue, long parentIssueId, @Nullable SubIssuesSummaryDTO parentSummary) {
        Optional<Issue> parentOpt = issueRepository.findById(parentIssueId);
        if (parentOpt.isEmpty()) {
            logger.debug("Parent issue {} not found in database, skipping link", parentIssueId);
            return;
        }

        Issue parentIssue = parentOpt.get();
        subIssue.setParentIssue(parentIssue);
        issueRepository.save(subIssue);

        updateParentSummary(parentIssue, parentSummary);

        logger.info("Linked sub-issue #{} to parent #{}", subIssue.getNumber(), parentIssue.getNumber());
    }

    private void unlinkSubIssueFromParent(
        Issue subIssue,
        long parentIssueId,
        @Nullable SubIssuesSummaryDTO parentSummary
    ) {
        Issue currentParent = subIssue.getParentIssue();
        if (currentParent != null) {
            logger.info("Unlinking sub-issue #{} from parent #{}", subIssue.getNumber(), currentParent.getNumber());
        }

        subIssue.setParentIssue(null);
        issueRepository.save(subIssue);

        if (parentSummary != null) {
            issueRepository.findById(parentIssueId).ifPresent(parent -> updateParentSummary(parent, parentSummary));
        }
    }

    private void updateParentSummary(Issue parentIssue, @Nullable SubIssuesSummaryDTO summary) {
        if (summary == null) {
            return;
        }

        parentIssue.setSubIssuesTotal(summary.total());
        parentIssue.setSubIssuesCompleted(summary.completed());
        parentIssue.setSubIssuesPercentCompleted(summary.percentCompleted());
        issueRepository.save(parentIssue);

        logger.debug("Updated parent #{} summary: {}", parentIssue.getNumber(), summary);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GRAPHQL BULK SYNC
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sync all sub-issue relationships for a workspace via GraphQL.
     * <p>
     * <b>Transaction Strategy:</b> This method is NOT transactional at the top
     * level.
     * GraphQL HTTP calls are made outside any transaction to avoid blocking DB
     * connections.
     * Individual database writes use separate transactions.
     * <p>
     * <b>Cooldown:</b> Respects sync cooldown to avoid excessive API calls.
     *
     * @param workspaceId The workspace to sync
     * @return total number of relationships synchronized, or -1 if skipped due to
     *         cooldown
     */
    public int syncSubIssuesForWorkspace(Long workspaceId) {
        // Load workspace info outside transaction (read-only)
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));

        // Check cooldown
        if (!shouldSync(workspace)) {
            logger.debug(
                "Skipping sub-issues sync for workspace '{}' due to cooldown (last sync: {})",
                workspace.getDisplayName(),
                workspace.getSubIssuesSyncedAt()
            );
            return -1;
        }

        List<Repository> repositories = repositoryRepository.findActiveByWorkspaceId(workspaceId);
        if (repositories.isEmpty()) {
            logger.debug("No repositories found for workspace {}", workspaceId);
            // Still update timestamp to prevent repeated empty checks
            updateSyncTimestamp(workspaceId);
            return 0;
        }

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);

        logger.info(
            "Starting sub-issue sync for workspace '{}' with {} repositories",
            workspace.getDisplayName(),
            repositories.size()
        );

        int totalLinked = 0;
        int failedRepos = 0;

        for (Repository repo : repositories) {
            try {
                totalLinked += syncSubIssuesForRepository(client, repo);
            } catch (Exception e) {
                failedRepos++;
                logger.error("Error syncing sub-issues for repository {}: {}", repo.getNameWithOwner(), e.getMessage());
            }
        }

        // Only update timestamp if at least some repos succeeded
        if (failedRepos < repositories.size()) {
            updateSyncTimestamp(workspaceId);
        }

        logger.info(
            "Completed sub-issue sync for workspace '{}': {} relationships synced ({} repos failed)",
            workspace.getDisplayName(),
            totalLinked,
            failedRepos
        );

        return totalLinked;
    }

    private boolean shouldSync(Workspace workspace) {
        if (workspace.getSubIssuesSyncedAt() == null) {
            return true; // Never synced before
        }
        var cooldownTime = Instant.now().minusSeconds(syncCooldownInMinutes * 60L);
        return workspace.getSubIssuesSyncedAt().isBefore(cooldownTime);
    }

    @Transactional
    public void updateSyncTimestamp(Long workspaceId) {
        workspaceRepository
            .findById(workspaceId)
            .ifPresent(ws -> {
                ws.setSubIssuesSyncedAt(Instant.now());
                workspaceRepository.save(ws);
            });
    }

    private int syncSubIssuesForRepository(HttpGraphQlClient client, Repository repository) {
        String[] parts = repository.getNameWithOwner().split("/");
        if (parts.length != 2) {
            logger.warn("Invalid repository name format: {}", repository.getNameWithOwner());
            return 0;
        }

        String owner = parts[0];
        String name = parts[1];
        String cursor = null;
        boolean hasNextPage = true;
        int linkedCount = 0;

        while (hasNextPage) {
            try {
                // GraphQL call OUTSIDE of @Transactional to avoid blocking DB connection
                IssueConnection issueConnection = client
                    .documentName(GET_SUB_ISSUES_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", GRAPHQL_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("repository.issues")
                    .toEntity(IssueConnection.class)
                    .block(GRAPHQL_TIMEOUT);

                if (issueConnection == null) {
                    logger.warn("No response from GraphQL for repository {}", repository.getNameWithOwner());
                    break;
                }

                hasNextPage = issueConnection.getPageInfo().getHasNextPage();
                cursor = issueConnection.getPageInfo().getEndCursor();

                // Process each page in its own transaction
                linkedCount += processIssueNodesInTransaction(issueConnection, repository);
            } catch (Exception e) {
                logger.error(
                    "Error syncing sub-issues for repository {}: {}",
                    repository.getNameWithOwner(),
                    e.getMessage()
                );
                break;
            }
        }

        return linkedCount;
    }

    /**
     * Process a page of issues in a separate transaction.
     * <p>
     * Using REQUIRES_NEW ensures each page is committed independently,
     * providing better resilience if a single page fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected int processIssueNodesInTransaction(IssueConnection issueConnection, Repository repository) {
        if (issueConnection.getNodes() == null) {
            return 0;
        }

        int linkedCount = 0;

        for (var graphQlIssue : issueConnection.getNodes()) {
            if (graphQlIssue.getParent() != null) {
                linkedCount += processParentRelationship(graphQlIssue, repository);
            }

            if (graphQlIssue.getSubIssuesSummary() != null) {
                processSubIssuesSummary(graphQlIssue);
            }
        }

        return linkedCount;
    }

    private int processParentRelationship(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Issue graphQlIssue,
        Repository repository
    ) {
        long issueDatabaseId = graphQlIssue.getDatabaseId().longValue();
        long parentDatabaseId = graphQlIssue.getParent().getDatabaseId().longValue();

        Optional<Issue> issueOpt = issueRepository.findById(issueDatabaseId);
        Optional<Issue> parentOpt = issueRepository.findById(parentDatabaseId);

        if (issueOpt.isPresent() && parentOpt.isPresent()) {
            Issue issue = issueOpt.get();
            Issue parent = parentOpt.get();

            if (!parent.equals(issue.getParentIssue())) {
                issue.setParentIssue(parent);
                issueRepository.save(issue);

                logger.debug(
                    "Linked issue #{} to parent #{} in {}",
                    issue.getNumber(),
                    parent.getNumber(),
                    repository.getNameWithOwner()
                );
                return 1;
            }
        }

        return 0;
    }

    private void processSubIssuesSummary(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Issue graphQlIssue
    ) {
        SubIssuesSummary summary = graphQlIssue.getSubIssuesSummary();
        if (summary == null || summary.getTotal() == 0) {
            return;
        }

        long databaseId = graphQlIssue.getDatabaseId().longValue();
        issueRepository
            .findById(databaseId)
            .ifPresent(issue -> {
                issue.setSubIssuesTotal(summary.getTotal());
                issue.setSubIssuesCompleted(summary.getCompleted());
                issue.setSubIssuesPercentCompleted(summary.getPercentCompleted());
                issueRepository.save(issue);

                logger.debug(
                    "Updated issue #{} summary: {}/{} sub-issues",
                    issue.getNumber(),
                    summary.getCompleted(),
                    summary.getTotal()
                );
            });
    }
}
