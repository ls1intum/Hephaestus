package de.tum.in.www1.hephaestus.gitprovider.issuedependency.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.*;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.WorkspaceSyncMetadata;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueConnection;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
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

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueDependencySyncService.class);

    /** Document name for graphql-documents/GetIssueDependencies.graphql */
    private static final String GET_DEPENDENCIES_DOCUMENT = "GetIssueDependencies";

    private final IssueRepository issueRepository;
    private final RepositoryRepository repositoryRepository;
    private final SyncTargetProvider syncTargetProvider;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final int syncCooldownInMinutes;
    private final GitHubIssueDependencySyncService self;

    public GitHubIssueDependencySyncService(
        IssueRepository issueRepository,
        RepositoryRepository repositoryRepository,
        SyncTargetProvider syncTargetProvider,
        GitHubGraphQlClientProvider graphQlClientProvider,
        @Value("${monitoring.sync-cooldown-in-minutes}") int syncCooldownInMinutes,
        @Lazy GitHubIssueDependencySyncService self
    ) {
        this.issueRepository = issueRepository;
        this.repositoryRepository = repositoryRepository;
        this.syncTargetProvider = syncTargetProvider;
        this.graphQlClientProvider = graphQlClientProvider;
        this.syncCooldownInMinutes = syncCooldownInMinutes;
        this.self = self;
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
        log.info(
            "Processing issue dependency event: blockedIssue={}, blockingIssue={}, isBlock={}",
            blockedIssueId,
            blockingIssueId,
            isBlock
        );

        Optional<Issue> blockedIssueOpt = issueRepository.findById(blockedIssueId);
        if (blockedIssueOpt.isEmpty()) {
            log.debug("Blocked issue {} not found in database, skipping", blockedIssueId);
            return;
        }

        Optional<Issue> blockingIssueOpt = issueRepository.findById(blockingIssueId);
        if (blockingIssueOpt.isEmpty()) {
            log.debug("Blocking issue {} not found in database, skipping", blockingIssueId);
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
            log.debug(
                "Skipping issue dependencies sync for workspace '{}' due to cooldown (last sync: {})",
                metadata.displayName(),
                metadata.issueDependenciesSyncedAt()
            );
            return -1;
        }

        // Get repository names via SPI (workspace package implements this)
        List<String> repositoryNames = syncTargetProvider.getRepositoryNamesForWorkspace(workspaceId);
        if (repositoryNames.isEmpty()) {
            log.debug("No repositories found for workspace {}", workspaceId);
            // Still update timestamp to prevent repeated empty checks
            updateSyncTimestamp(workspaceId);
            return 0;
        }

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);
        int totalSynced = 0;
        int failedRepos = 0;

        for (String repoNameWithOwner : repositoryNames) {
            Optional<Repository> repoOpt = repositoryRepository.findByNameWithOwner(repoNameWithOwner);
            if (repoOpt.isEmpty()) {
                log.warn("Repository {} not found in database, skipping", sanitizeForLog(repoNameWithOwner));
                continue;
            }

            try {
                int synced = syncRepositoryDependencies(client, repoOpt.get());
                totalSynced += synced;
            } catch (Exception e) {
                failedRepos++;
                log.error("Error syncing dependencies for repository {}: {}", repoNameWithOwner, e.getMessage());
            }
        }

        // Only update timestamp if at least some repos succeeded
        if (failedRepos < repositoryNames.size()) {
            updateSyncTimestamp(workspaceId);
        }

        log.info(
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
        String safeNameWithOwner = sanitizeForLog(repo.getNameWithOwner());
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(repo.getNameWithOwner());
        if (parsedName.isEmpty()) {
            log.warn("Invalid repository name format: {}", safeNameWithOwner);
            return 0;
        }
        String owner = parsedName.get().owner();
        String name = parsedName.get().name();
        String after = null;
        boolean hasNextPage = true;
        int totalSynced = 0;
        int pageCount = 0;

        while (hasNextPage) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit ({}) for repository {}, stopping",
                    MAX_PAGINATION_PAGES,
                    safeNameWithOwner
                );
                break;
            }

            // GraphQL call OUTSIDE of @Transactional to avoid blocking DB connection
            GHIssueConnection issueConnection = client
                .documentName(GET_DEPENDENCIES_DOCUMENT)
                .variable("owner", owner)
                .variable("name", name)
                .variable("first", LARGE_PAGE_SIZE)
                .variable("after", after)
                .retrieve("repository.issues")
                .toEntity(GHIssueConnection.class)
                .block(GRAPHQL_TIMEOUT);

            if (issueConnection == null || issueConnection.getNodes() == null) {
                log.warn("No response from GraphQL for repository {}", safeNameWithOwner);
                break;
            }

            hasNextPage = issueConnection.getPageInfo().getHasNextPage();
            after = issueConnection.getPageInfo().getEndCursor();

            // Process each page in its own transaction (call through proxy for @Transactional to work)
            totalSynced += self.processIssueDependenciesPage(issueConnection);
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
    protected int processIssueDependenciesPage(GHIssueConnection issueConnection) {
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
    private int processIssueDependencies(GHIssue graphQlIssue) {
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
            log.debug(
                "Issue #{} is already blocked by #{}, skipping",
                blockedIssue.getNumber(),
                blockingIssue.getNumber()
            );
            return;
        }

        blockedIssue.getBlockedBy().add(blockingIssue);
        issueRepository.save(blockedIssue);

        log.info(
            "Added blocking relationship: #{} is now blocked by #{}",
            blockedIssue.getNumber(),
            blockingIssue.getNumber()
        );
    }

    private void removeBlockingRelationship(Issue blockedIssue, Issue blockingIssue) {
        boolean removed = blockedIssue.getBlockedBy().remove(blockingIssue);

        if (removed) {
            issueRepository.save(blockedIssue);
            log.info(
                "Removed blocking relationship: #{} is no longer blocked by #{}",
                blockedIssue.getNumber(),
                blockingIssue.getNumber()
            );
        } else {
            log.debug(
                "Issue #{} was not blocked by #{}, nothing to remove",
                blockedIssue.getNumber(),
                blockingIssue.getNumber()
            );
        }
    }
}
