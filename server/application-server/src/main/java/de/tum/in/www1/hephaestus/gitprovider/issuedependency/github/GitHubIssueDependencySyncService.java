package de.tum.in.www1.hephaestus.gitprovider.issuedependency.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.WorkspaceSyncMetadata;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.IssueConnection;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for syncing GitHub issue dependency (blocked_by/blocking)
 * relationships.
 * <p>
 * GitHub's issue dependencies feature allows marking issues as blocked by other
 * issues, indicating that one issue must be resolved before another can be
 * worked on.
 * <p>
 * <b>Terminology:</b>
 * <ul>
 * <li><b>blocked issue</b> - The issue that cannot proceed until blockers are
 * resolved</li>
 * <li><b>blocking issue</b> - The issue that prevents work on another
 * issue</li>
 * </ul>
 * <p>
 * <b>NOTE (Dec 2025):</b> The {@code issue_dependencies} webhook event is
 * <b>STILL NOT AVAILABLE</b> for subscription in GitHub App settings.
 * GitHub shipped the "Blocked by" UI without webhook/API event support
 * (see <a href="https://github.com/orgs/community/discussions/165749">
 * Community Discussion #165749</a>). Until webhooks become available,
 * use {@link #syncDependenciesForWorkspace} for bulk GraphQL sync.
 */
@Service
public class GitHubIssueDependencySyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueDependencySyncService.class);
    private static final int GRAPHQL_PAGE_SIZE = 100;
    private static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(30);

    /** Document name for graphql-documents/GetIssueDependencies.graphql */
    private static final String GET_DEPENDENCIES_DOCUMENT = "GetIssueDependencies";

    private final IssueRepository issueRepository;
    private final RepositoryRepository repositoryRepository;
    private final SyncTargetProvider syncTargetProvider;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final int syncCooldownInMinutes;

    public GitHubIssueDependencySyncService(
        IssueRepository issueRepository,
        RepositoryRepository repositoryRepository,
        SyncTargetProvider syncTargetProvider,
        GitHubGraphQlClientProvider graphQlClientProvider,
        @Value("${monitoring.sync-cooldown-in-minutes}") int syncCooldownInMinutes
    ) {
        this.issueRepository = issueRepository;
        this.repositoryRepository = repositoryRepository;
        this.syncTargetProvider = syncTargetProvider;
        this.graphQlClientProvider = graphQlClientProvider;
        this.syncCooldownInMinutes = syncCooldownInMinutes;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // WEBHOOK EVENT PROCESSING (for when webhooks become available)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Process an issue_dependencies webhook event.
     * <p>
     * Creates or removes blocking relationships based on the event type.
     *
     * @param blockedIssueId  ID of the issue being blocked
     * @param blockingIssueId ID of the issue doing the blocking
     * @param isBlock         true if creating a block, false if removing
     */
    @Transactional
    public void processIssueDependencyEvent(long blockedIssueId, long blockingIssueId, boolean isBlock) {
        logger.info(
            "Processing issue dependency event: blockedIssue={}, blockingIssue={}, isBlock={}",
            blockedIssueId,
            blockingIssueId,
            isBlock
        );

        Optional<Issue> blockedIssueOpt = issueRepository.findById(blockedIssueId);
        if (blockedIssueOpt.isEmpty()) {
            logger.debug("Blocked issue {} not found in database, skipping", blockedIssueId);
            return;
        }

        Optional<Issue> blockingIssueOpt = issueRepository.findById(blockingIssueId);
        if (blockingIssueOpt.isEmpty()) {
            logger.debug("Blocking issue {} not found in database, skipping", blockingIssueId);
            return;
        }

        Issue blockedIssue = blockedIssueOpt.get();
        Issue blockingIssue = blockingIssueOpt.get();

        if (isBlock) {
            addBlockingRelationship(blockedIssue, blockingIssue);
        } else {
            removeBlockingRelationship(blockedIssue, blockingIssue);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GRAPHQL BULK SYNC
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sync all issue dependencies for a workspace via GraphQL.
     * <p>
     * This is the primary sync mechanism until issue_dependencies webhooks
     * become available in GitHub App settings.
     * <p>
     * <b>Transaction Strategy:</b> This method is NOT transactional at the top
     * level. GraphQL HTTP calls are made outside any transaction to avoid blocking
     * DB connections. Individual database writes use separate transactions.
     *
     * @param workspaceId The workspace to sync
     * @return Total relationships synced, or -1 if skipped due to cooldown
     */
    public int syncDependenciesForWorkspace(Long workspaceId) {
        Optional<WorkspaceSyncMetadata> metadataOpt = syncTargetProvider.getWorkspaceSyncMetadata(workspaceId);
        if (metadataOpt.isEmpty()) {
            throw new IllegalArgumentException("Workspace not found: " + workspaceId);
        }

        WorkspaceSyncMetadata metadata = metadataOpt.get();
        if (!metadata.needsIssueDependenciesSync(syncCooldownInMinutes)) {
            logger.debug(
                "Skipping issue dependencies sync for workspace '{}' due to cooldown (last sync: {})",
                metadata.displayName(),
                metadata.issueDependenciesSyncedAt()
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
        int totalSynced = 0;
        int failedRepos = 0;

        for (Repository repo : repositories) {
            try {
                int synced = syncRepositoryDependencies(client, repo);
                totalSynced += synced;
            } catch (Exception e) {
                failedRepos++;
                logger.error(
                    "Error syncing dependencies for repository {}: {}",
                    repo.getNameWithOwner(),
                    e.getMessage()
                );
            }
        }

        // Only update timestamp if at least some repos succeeded
        if (failedRepos < repositories.size()) {
            updateSyncTimestamp(workspaceId);
        }

        logger.info(
            "Synced {} issue dependency relationships for workspace {} ({} repos failed)",
            totalSynced,
            metadata.displayName(),
            failedRepos
        );
        return totalSynced;
    }

    /**
     * Update the sync timestamp for issue dependencies.
     */
    @Transactional
    public void updateSyncTimestamp(Long workspaceId) {
        syncTargetProvider.updateWorkspaceSyncTimestamp(
            workspaceId,
            SyncTargetProvider.SyncType.ISSUE_DEPENDENCIES,
            Instant.now()
        );
    }

    /**
     * Sync dependencies for a single repository.
     * Uses type-safe generated DTOs for GraphQL response handling.
     */
    private int syncRepositoryDependencies(HttpGraphQlClient client, Repository repo) {
        String[] parts = repo.getNameWithOwner().split("/");
        if (parts.length != 2) {
            logger.warn("Invalid repository name format: {}", repo.getNameWithOwner());
            return 0;
        }

        String owner = parts[0];
        String name = parts[1];
        String after = null;
        boolean hasNextPage = true;
        int totalSynced = 0;

        while (hasNextPage) {
            // GraphQL call OUTSIDE of @Transactional to avoid blocking DB connection
            IssueConnection issueConnection = client
                .documentName(GET_DEPENDENCIES_DOCUMENT)
                .variable("owner", owner)
                .variable("name", name)
                .variable("first", GRAPHQL_PAGE_SIZE)
                .variable("after", after)
                .retrieve("repository.issues")
                .toEntity(IssueConnection.class)
                .block(GRAPHQL_TIMEOUT);

            if (issueConnection == null || issueConnection.getNodes() == null) {
                logger.warn("No response from GraphQL for repository {}", repo.getNameWithOwner());
                break;
            }

            hasNextPage = issueConnection.getPageInfo().getHasNextPage();
            after = issueConnection.getPageInfo().getEndCursor();

            // Process each page in its own transaction
            totalSynced += processIssueDependenciesPage(issueConnection);
        }

        return totalSynced;
    }

    /**
     * Process a page of issue dependencies in a separate transaction.
     * <p>
     * Using REQUIRES_NEW ensures each page is committed independently,
     * providing better resilience if a single page fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected int processIssueDependenciesPage(IssueConnection issueConnection) {
        if (issueConnection.getNodes() == null) {
            return 0;
        }

        int synced = 0;
        for (var graphQlIssue : issueConnection.getNodes()) {
            synced += processIssueDependencies(graphQlIssue);
        }
        return synced;
    }

    /**
     * Process dependencies for a single issue from the GraphQL response.
     */
    private int processIssueDependencies(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Issue graphQlIssue
    ) {
        if (graphQlIssue.getFullDatabaseId() == null) {
            return 0;
        }

        long issueId = graphQlIssue.getFullDatabaseId().longValue();
        Optional<Issue> issueOpt = issueRepository.findById(issueId);
        if (issueOpt.isEmpty()) {
            return 0;
        }

        Issue issue = issueOpt.get();

        // Process blockedBy relationships
        var blockedBy = graphQlIssue.getBlockedBy();
        if (blockedBy == null || blockedBy.getNodes() == null) {
            return 0;
        }

        Set<Long> blockedByIds = blockedBy
            .getNodes()
            .stream()
            .filter(node -> node.getFullDatabaseId() != null)
            .map(node -> node.getFullDatabaseId().longValue())
            .collect(Collectors.toSet());

        return syncBlockedByRelationships(issue, blockedByIds);
    }

    /**
     * Synchronize blocked-by relationships for an issue.
     * Uses batch loading to avoid N+1 query problems.
     */
    private int syncBlockedByRelationships(Issue issue, Set<Long> expectedBlockerIds) {
        if (expectedBlockerIds.isEmpty()) {
            // Remove all existing relationships
            int removed = issue.getBlockedBy().size();
            if (removed > 0) {
                issue.getBlockedBy().clear();
                issueRepository.save(issue);
            }
            return 0;
        }

        // Batch load all blockers in one query (fixes N+1 problem)
        List<Issue> blockers = issueRepository.findAllById(expectedBlockerIds);
        Set<Long> foundBlockerIds = blockers.stream().map(Issue::getId).collect(Collectors.toSet());

        int changes = 0;

        // Add missing relationships
        for (Issue blocker : blockers) {
            if (!issue.getBlockedBy().contains(blocker)) {
                issue.getBlockedBy().add(blocker);
                changes++;
            }
        }

        // Remove stale relationships (blockers that should no longer exist)
        int initialSize = issue.getBlockedBy().size();
        issue.getBlockedBy().removeIf(blocker -> !foundBlockerIds.contains(blocker.getId()));
        int removedCount = initialSize - issue.getBlockedBy().size();
        changes += removedCount;

        if (changes > 0) {
            issueRepository.save(issue);
        }

        return changes;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    private void addBlockingRelationship(Issue blockedIssue, Issue blockingIssue) {
        if (blockedIssue.getBlockedBy().contains(blockingIssue)) {
            logger.debug(
                "Issue #{} is already blocked by #{}, skipping",
                blockedIssue.getNumber(),
                blockingIssue.getNumber()
            );
            return;
        }

        blockedIssue.getBlockedBy().add(blockingIssue);
        issueRepository.save(blockedIssue);

        logger.info(
            "Added blocking relationship: #{} is now blocked by #{}",
            blockedIssue.getNumber(),
            blockingIssue.getNumber()
        );
    }

    private void removeBlockingRelationship(Issue blockedIssue, Issue blockingIssue) {
        boolean removed = blockedIssue.getBlockedBy().remove(blockingIssue);

        if (removed) {
            issueRepository.save(blockedIssue);
            logger.info(
                "Removed blocking relationship: #{} is no longer blocked by #{}",
                blockedIssue.getNumber(),
                blockingIssue.getNumber()
            );
        } else {
            logger.debug(
                "Issue #{} was not blocked by #{}, nothing to remove",
                blockedIssue.getNumber(),
                blockingIssue.getNumber()
            );
        }
    }
}
