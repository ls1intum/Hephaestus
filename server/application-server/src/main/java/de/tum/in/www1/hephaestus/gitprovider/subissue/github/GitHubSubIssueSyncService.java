package de.tum.in.www1.hephaestus.gitprovider.subissue.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.*;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubRepositoryNameParser.RepositoryOwnerAndName;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncMetadata;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssue;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHSubIssuesSummary;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issue.IssueRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
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
 * <li>Scope-level GraphQL sync to fetch all relationships</li>
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

    private static final Logger log = LoggerFactory.getLogger(GitHubSubIssueSyncService.class);

    /** Document name for graphql-documents/GetSubIssuesForRepository.graphql */
    private static final String GET_SUB_ISSUES_DOCUMENT = "GetSubIssuesForRepository";

    private final IssueRepository issueRepository;
    private final RepositoryRepository repositoryRepository;
    private final SyncTargetProvider syncTargetProvider;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final int syncCooldownInMinutes;

    public GitHubSubIssueSyncService(
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
        log.info(
            "Processing sub-issue event: subIssueId={}, parentIssueId={}, isLink={}",
            subIssueId,
            parentIssueId,
            isLink
        );

        Optional<Issue> subIssueOpt = issueRepository.findById(subIssueId);
        if (subIssueOpt.isEmpty()) {
            log.debug("Sub-issue not found in database, skipping: issueId={}", subIssueId);
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
            log.debug("Parent issue not found in database, skipping link: issueId={}", parentIssueId);
            return;
        }

        Issue parentIssue = parentOpt.get();
        subIssue.setParentIssue(parentIssue);
        issueRepository.save(subIssue);

        updateParentSummary(parentIssue, parentSummary);

        log.info("Linked sub-issue to parent: subIssueNumber={}, parentIssueNumber={}", subIssue.getNumber(), parentIssue.getNumber());
    }

    private void unlinkSubIssueFromParent(
        Issue subIssue,
        long parentIssueId,
        @Nullable SubIssuesSummaryDTO parentSummary
    ) {
        Issue currentParent = subIssue.getParentIssue();
        if (currentParent != null) {
            log.info("Unlinking sub-issue from parent: subIssueNumber={}, parentIssueNumber={}", subIssue.getNumber(), currentParent.getNumber());
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

        log.debug("Updated parent issue summary: issueNumber={}, summary={}", parentIssue.getNumber(), summary);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GRAPHQL BULK SYNC
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Sync all sub-issue relationships for a scope via GraphQL.
     * <p>
     * <b>Transaction Strategy:</b> This method is NOT transactional at the top
     * level.
     * GraphQL HTTP calls are made outside any transaction to avoid blocking DB
     * connections.
     * Individual database writes use separate transactions.
     * <p>
     * <b>Cooldown:</b> Respects sync cooldown to avoid excessive API calls.
     *
     * @param scopeId The scope to sync
     * @return total number of relationships synchronized, or -1 if skipped due to
     *         cooldown
     */
    public int syncSubIssuesForScope(Long scopeId) {
        // Load scope metadata via SPI
        Optional<SyncMetadata> metadataOpt = syncTargetProvider.getSyncMetadata(scopeId);
        if (metadataOpt.isEmpty()) {
            throw new IllegalArgumentException("Scope not found: " + scopeId);
        }

        SyncMetadata metadata = metadataOpt.get();

        // Check cooldown
        if (!metadata.needsSubIssuesSync(syncCooldownInMinutes)) {
            log.debug(
                "Skipping sub-issues sync due to cooldown: scopeId={}, lastSyncedAt={}",
                scopeId,
                metadata.subIssuesSyncedAt()
            );
            return -1;
        }

        // Get repository names via SPI (consuming module implements this)
        List<String> repositoryNames = syncTargetProvider.getRepositoryNamesForScope(scopeId);
        if (repositoryNames.isEmpty()) {
            log.debug("No repositories found for scope: scopeId={}", scopeId);
            // Still update timestamp to prevent repeated empty checks
            updateSyncTimestamp(scopeId);
            return 0;
        }

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

        log.info("Starting sub-issue sync: scopeId={}, repoCount={}", scopeId, repositoryNames.size());

        int totalLinked = 0;
        int failedRepoCount = 0;

        for (String repoNameWithOwner : repositoryNames) {
            Optional<Repository> repoOpt = repositoryRepository.findByNameWithOwner(repoNameWithOwner);
            if (repoOpt.isEmpty()) {
                log.warn("Repository not found in database, skipping: repoName={}", sanitizeForLog(repoNameWithOwner));
                continue;
            }

            try {
                totalLinked += syncSubIssuesForRepository(client, repoOpt.get());
            } catch (Exception e) {
                failedRepoCount++;
                log.error("Failed to sync sub-issues: repoName={}", sanitizeForLog(repoNameWithOwner), e);
            }
        }

        // Only update timestamp if at least some repos succeeded
        if (failedRepoCount < repositoryNames.size()) {
            updateSyncTimestamp(scopeId);
        }

        log.info(
            "Completed sub-issue sync: scopeId={}, subIssueCount={}, failedRepoCount={}",
            scopeId,
            totalLinked,
            failedRepoCount
        );

        return totalLinked;
    }

    @Transactional
    public void updateSyncTimestamp(Long scopeId) {
        syncTargetProvider.updateScopeSyncTimestamp(
            scopeId,
            SyncTargetProvider.SyncType.SUB_ISSUES,
            Instant.now()
        );
    }

    private int syncSubIssuesForRepository(HttpGraphQlClient client, Repository repository) {
        Optional<RepositoryOwnerAndName> parsedName = GitHubRepositoryNameParser.parse(repository.getNameWithOwner());
        if (parsedName.isEmpty()) {
            log.warn("Invalid repository name format: repoName={}", sanitizeForLog(repository.getNameWithOwner()));
            return 0;
        }
        String owner = parsedName.get().owner();
        String name = parsedName.get().name();
        String cursor = null;
        boolean hasNextPage = true;
        int linkedCount = 0;

        int pageCount = 0;

        while (hasNextPage) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for sub-issue sync: repoName={}, limit={}",
                    sanitizeForLog(repository.getNameWithOwner()),
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            try {
                // GraphQL call OUTSIDE of @Transactional to avoid blocking DB connection
                GHIssueConnection issueConnection = client
                    .documentName(GET_SUB_ISSUES_DOCUMENT)
                    .variable("owner", owner)
                    .variable("name", name)
                    .variable("first", LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("repository.issues")
                    .toEntity(GHIssueConnection.class)
                    .block(GRAPHQL_TIMEOUT);

                if (issueConnection == null) {
                    log.warn(
                        "No response from GraphQL for sub-issue sync: repoName={}",
                        sanitizeForLog(repository.getNameWithOwner())
                    );
                    break;
                }

                hasNextPage = issueConnection.getPageInfo().getHasNextPage();
                cursor = issueConnection.getPageInfo().getEndCursor();

                // Process each page in its own transaction
                linkedCount += processIssueNodesInTransaction(issueConnection, repository);
            } catch (Exception e) {
                log.error(
                    "Failed to sync sub-issues for repository: repoName={}",
                    sanitizeForLog(repository.getNameWithOwner()),
                    e
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
    protected int processIssueNodesInTransaction(GHIssueConnection issueConnection, Repository repository) {
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

    private int processParentRelationship(GHIssue graphQlIssue, Repository repository) {
        long issueDatabaseId = graphQlIssue.getFullDatabaseId().longValue();
        long parentDatabaseId = graphQlIssue.getParent().getFullDatabaseId().longValue();

        Optional<Issue> issueOpt = issueRepository.findById(issueDatabaseId);
        Optional<Issue> parentOpt = issueRepository.findById(parentDatabaseId);

        if (issueOpt.isPresent() && parentOpt.isPresent()) {
            Issue issue = issueOpt.get();
            Issue parent = parentOpt.get();

            if (!parent.equals(issue.getParentIssue())) {
                issue.setParentIssue(parent);
                issueRepository.save(issue);

                log.debug(
                    "Linked issue to parent: issueNumber={}, parentNumber={}, repoName={}",
                    issue.getNumber(),
                    parent.getNumber(),
                    repository.getNameWithOwner()
                );
                return 1;
            }
        }

        return 0;
    }

    private void processSubIssuesSummary(GHIssue graphQlIssue) {
        GHSubIssuesSummary summary = graphQlIssue.getSubIssuesSummary();
        if (summary == null || summary.getTotal() == 0) {
            return;
        }

        long databaseId = graphQlIssue.getFullDatabaseId().longValue();
        issueRepository
            .findById(databaseId)
            .ifPresent(issue -> {
                issue.setSubIssuesTotal(summary.getTotal());
                issue.setSubIssuesCompleted(summary.getCompleted());
                issue.setSubIssuesPercentCompleted(summary.getPercentCompleted());
                issueRepository.save(issue);

                log.debug(
                    "Updated issue sub-issues summary: issueNumber={}, completed={}, total={}",
                    issue.getNumber(),
                    summary.getCompleted(),
                    summary.getTotal()
                );
            });
    }
}
