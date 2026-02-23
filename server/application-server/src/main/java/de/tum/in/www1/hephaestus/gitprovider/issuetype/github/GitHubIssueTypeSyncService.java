package de.tum.in.www1.hephaestus.gitprovider.issuetype.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.JITTER_FACTOR;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.LARGE_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_INITIAL_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_BACKOFF;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.TRANSPORT_MAX_RETRIES;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.adaptPageSize;

import de.tum.in.www1.hephaestus.gitprovider.common.exception.InstallationNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.common.github.ExponentialBackoff;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubExceptionClassifier.ClassificationResult;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlSyncCoordinator;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlSyncCoordinator.GraphQlClassificationContext;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncProperties;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubTransportErrors;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GraphQlConnectionOverflowDetector;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.SyncMetadata;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueTypeColor;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueTypeConnection;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueType;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueTypeRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.SyncSchedulerProperties;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.ClientGraphQlResponse;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Service for syncing GitHub Issue Types from the organization level via GraphQL.
 */
@Service
public class GitHubIssueTypeSyncService {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueTypeSyncService.class);
    private static final String GET_ISSUE_TYPES_DOCUMENT = "GetOrganizationIssueTypes";

    private final IssueTypeRepository issueTypeRepository;
    private final OrganizationRepository organizationRepository;
    private final SyncTargetProvider syncTargetProvider;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubSyncProperties syncProperties;
    private final GitHubExceptionClassifier exceptionClassifier;
    private final SyncSchedulerProperties syncSchedulerProperties;
    private final GitHubGraphQlSyncCoordinator graphQlSyncHelper;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    public GitHubIssueTypeSyncService(
        IssueTypeRepository issueTypeRepository,
        OrganizationRepository organizationRepository,
        SyncTargetProvider syncTargetProvider,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubSyncProperties syncProperties,
        GitHubExceptionClassifier exceptionClassifier,
        SyncSchedulerProperties syncSchedulerProperties,
        GitHubGraphQlSyncCoordinator graphQlSyncHelper
    ) {
        this.issueTypeRepository = issueTypeRepository;
        this.organizationRepository = organizationRepository;
        this.syncTargetProvider = syncTargetProvider;
        this.graphQlClientProvider = graphQlClientProvider;
        this.syncProperties = syncProperties;
        this.exceptionClassifier = exceptionClassifier;
        this.syncSchedulerProperties = syncSchedulerProperties;
        this.graphQlSyncHelper = graphQlSyncHelper;
    }

    /**
     * Sync all issue types for a scope from its GitHub organization.
     *
     * @param scopeId The scope to sync issue types for
     * @return Number of issue types synced, or -1 if skipped due to cooldown
     */
    @Transactional
    public int syncIssueTypesForScope(Long scopeId) {
        Optional<SyncMetadata> metadataOpt = syncTargetProvider.getSyncMetadata(scopeId);
        if (metadataOpt.isEmpty()) {
            log.warn("Scope not found, cannot sync issue types: scopeId={}", scopeId);
            return 0;
        }

        SyncMetadata metadata = metadataOpt.get();
        String orgLogin = metadata.organizationLogin();
        if (orgLogin == null) {
            log.debug("Skipped issue type sync: reason=noOrganization, scopeId={}", scopeId);
            return 0;
        }
        String safeOrgLogin = sanitizeForLog(orgLogin);

        if (!metadata.needsIssueTypesSync(syncSchedulerProperties.cooldownMinutes())) {
            log.debug("Skipped issue type sync: reason=cooldownActive, scopeId={}", scopeId);
            return -1;
        }

        // Load organization from gitprovider's repository
        Organization organization = organizationRepository.findById(metadata.organizationId()).orElse(null);
        if (organization == null) {
            log.warn("Organization not found in database: orgLogin={}", safeOrgLogin);
            return 0;
        }

        log.info("Starting issue type sync: orgLogin={}", safeOrgLogin);

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

        try {
            Set<String> syncedIds = new HashSet<>();
            int totalSynced = 0;
            int reportedTotalCount = -1;
            String cursor = null;
            boolean hasNextPage = true;
            int pageCount = 0;
            int retryAttempt = 0;

            while (hasNextPage) {
                pageCount++;
                if (pageCount >= MAX_PAGINATION_PAGES) {
                    log.warn(
                        "Reached maximum pagination limit for issue type sync: orgLogin={}, limit={}",
                        safeOrgLogin,
                        MAX_PAGINATION_PAGES
                    );
                    break;
                }

                final String currentCursor = cursor;
                final int currentPage = pageCount;

                ClientGraphQlResponse graphQlResponse = Mono.defer(() ->
                    client
                        .documentName(GET_ISSUE_TYPES_DOCUMENT)
                        .variable("login", orgLogin)
                        .variable(
                            "first",
                            adaptPageSize(LARGE_PAGE_SIZE, graphQlClientProvider.getRateLimitRemaining(scopeId))
                        )
                        .variable("after", currentCursor)
                        .execute()
                )
                    .retryWhen(
                        Retry.backoff(TRANSPORT_MAX_RETRIES, TRANSPORT_INITIAL_BACKOFF)
                            .maxBackoff(TRANSPORT_MAX_BACKOFF)
                            .jitter(JITTER_FACTOR)
                            .filter(GitHubTransportErrors::isTransportError)
                            .doBeforeRetry(signal ->
                                log.warn(
                                    "Retrying after transport error: context=issueTypeSync, orgLogin={}, page={}, attempt={}, error={}",
                                    safeOrgLogin,
                                    currentPage,
                                    signal.totalRetries() + 1,
                                    signal.failure().getMessage()
                                )
                            )
                    )
                    .block(syncProperties.graphqlTimeout());

                if (graphQlResponse == null || !graphQlResponse.isValid()) {
                    ClassificationResult classification = graphQlSyncHelper.classifyGraphQlErrors(graphQlResponse);
                    if (classification != null) {
                        if (
                            graphQlSyncHelper.handleGraphQlClassification(
                                new GraphQlClassificationContext(
                                    classification,
                                    retryAttempt,
                                    MAX_RETRY_ATTEMPTS,
                                    "issue type sync",
                                    "orgLogin",
                                    safeOrgLogin,
                                    log
                                )
                            )
                        ) {
                            retryAttempt++;
                            continue;
                        }
                        break;
                    }
                    log.warn(
                        "Received invalid GraphQL response: orgLogin={}, errors={}",
                        safeOrgLogin,
                        graphQlResponse != null ? graphQlResponse.getErrors() : "null"
                    );
                    break;
                }

                // Track rate limit from response
                graphQlClientProvider.trackRateLimit(scopeId, graphQlResponse);

                // Check if we should pause due to rate limiting
                if (graphQlClientProvider.isRateLimitCritical(scopeId)) {
                    if (
                        !graphQlSyncHelper.waitForRateLimitIfNeeded(
                            scopeId,
                            "issue type sync",
                            "orgLogin",
                            safeOrgLogin,
                            log
                        )
                    ) {
                        break;
                    }
                }

                GHIssueTypeConnection response = graphQlResponse
                    .field("organization.issueTypes")
                    .toEntity(GHIssueTypeConnection.class);

                if (response == null || response.getNodes() == null) {
                    break;
                }

                if (reportedTotalCount < 0) {
                    reportedTotalCount = response.getTotalCount();
                }

                for (var graphQlType : response.getNodes()) {
                    String id = graphQlType.getId();
                    syncIssueType(graphQlType, organization);
                    syncedIds.add(id);
                    totalSynced++;
                }

                var pageInfo = response.getPageInfo();
                hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
                retryAttempt = 0;
            }

            // Check for overflow: did we fetch fewer items than GitHub reported?
            if (reportedTotalCount >= 0) {
                GraphQlConnectionOverflowDetector.check("issueTypes", totalSynced, reportedTotalCount, safeOrgLogin);
            }

            removeDeletedIssueTypes(organization.getId(), syncedIds);
            syncTargetProvider.updateScopeSyncTimestamp(
                scopeId,
                SyncTargetProvider.SyncType.ISSUE_TYPES,
                Instant.now()
            );

            log.info("Completed issue type sync: orgLogin={}, issueTypeCount={}", safeOrgLogin, totalSynced);
            return totalSynced;
        } catch (InstallationNotFoundException e) {
            log.warn("Installation not found for scope {}, skipping issue type sync", scopeId);
            return 0;
        } catch (Exception e) {
            ClassificationResult classification = exceptionClassifier.classifyWithDetails(e);
            if (
                !graphQlSyncHelper.handleGraphQlClassification(
                    new GraphQlClassificationContext(
                        classification,
                        0,
                        MAX_RETRY_ATTEMPTS,
                        "issue type sync",
                        "orgLogin",
                        safeOrgLogin,
                        log
                    )
                )
            ) {
                return 0;
            }
            return 0;
        }
    }

    private void removeDeletedIssueTypes(Long organizationId, Set<String> syncedIds) {
        if (syncedIds.isEmpty()) {
            return;
        }
        List<IssueType> existingTypes = issueTypeRepository.findAllByOrganizationId(organizationId);
        for (IssueType existingType : existingTypes) {
            if (!syncedIds.contains(existingType.getId())) {
                issueTypeRepository.delete(existingType);
                log.debug("Removed deleted issue type: issueTypeName={}", existingType.getName());
            }
        }
    }

    private void syncIssueType(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHIssueType graphQlType,
        Organization organization
    ) {
        String id = graphQlType.getId();

        IssueType issueType = issueTypeRepository
            .findById(id)
            .orElseGet(() -> {
                IssueType newType = new IssueType();
                newType.setId(id);
                return newType;
            });

        issueType.setName(graphQlType.getName());
        issueType.setDescription(graphQlType.getDescription());
        issueType.setColor(convertColor(graphQlType.getColor()));
        issueType.setEnabled(Boolean.TRUE.equals(graphQlType.getIsEnabled()));
        issueType.setOrganization(organization);

        // Mark sync timestamp
        issueType.setLastSyncAt(Instant.now());

        issueTypeRepository.save(issueType);
    }

    /**
     * Find or create an issue type from webhook payload data.
     */
    @Transactional
    public IssueType findOrCreateFromWebhook(
        String nodeId,
        String name,
        String description,
        String color,
        boolean isEnabled,
        Organization organization
    ) {
        return issueTypeRepository
            .findById(nodeId)
            .orElseGet(() -> {
                IssueType newType = new IssueType();
                newType.setId(nodeId);
                newType.setName(name);
                newType.setDescription(description);
                newType.setColor(parseColor(color));
                newType.setEnabled(isEnabled);
                newType.setOrganization(organization);
                return issueTypeRepository.save(newType);
            });
    }

    public Optional<IssueType> findByNodeId(String nodeId) {
        return issueTypeRepository.findById(nodeId);
    }

    private IssueType.Color convertColor(GHIssueTypeColor graphQlColor) {
        if (graphQlColor == null) {
            return IssueType.Color.GRAY;
        }
        try {
            return IssueType.Color.valueOf(graphQlColor.name());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown issue type color, using GRAY as fallback: color={}", graphQlColor);
            return IssueType.Color.GRAY;
        }
    }

    private IssueType.Color parseColor(String colorString) {
        if (colorString == null || colorString.isBlank()) {
            return IssueType.Color.GRAY;
        }
        try {
            return IssueType.Color.valueOf(colorString.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown issue type color, using GRAY as fallback: color={}", colorString);
            return IssueType.Color.GRAY;
        }
    }
}
