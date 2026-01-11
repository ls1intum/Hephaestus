package de.tum.in.www1.hephaestus.gitprovider.issuetype.github;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.*;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.SyncTargetProvider.WorkspaceSyncMetadata;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.IssueTypeColor;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.IssueTypeConnection;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueType;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueTypeRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final int syncCooldownInMinutes;

    public GitHubIssueTypeSyncService(
        IssueTypeRepository issueTypeRepository,
        OrganizationRepository organizationRepository,
        SyncTargetProvider syncTargetProvider,
        GitHubGraphQlClientProvider graphQlClientProvider,
        @Value("${monitoring.sync-cooldown-in-minutes}") int syncCooldownInMinutes
    ) {
        this.issueTypeRepository = issueTypeRepository;
        this.organizationRepository = organizationRepository;
        this.syncTargetProvider = syncTargetProvider;
        this.graphQlClientProvider = graphQlClientProvider;
        this.syncCooldownInMinutes = syncCooldownInMinutes;
    }

    /**
     * Sync all issue types for a workspace from its GitHub organization.
     *
     * @param workspaceId The workspace to sync issue types for
     * @return Number of issue types synced, or -1 if skipped due to cooldown
     */
    @Transactional
    public int syncIssueTypesForWorkspace(Long workspaceId) {
        Optional<WorkspaceSyncMetadata> metadataOpt = syncTargetProvider.getWorkspaceSyncMetadata(workspaceId);
        if (metadataOpt.isEmpty()) {
            log.warn("Workspace {} not found, cannot sync issue types", workspaceId);
            return 0;
        }

        WorkspaceSyncMetadata metadata = metadataOpt.get();
        String orgLogin = metadata.organizationLogin();
        if (orgLogin == null) {
            log.debug("Workspace {} has no organization, skipping issue type sync", workspaceId);
            return 0;
        }

        if (!metadata.needsIssueTypesSync(syncCooldownInMinutes)) {
            log.debug("Skipping issue type sync for workspace {} - cooldown active", workspaceId);
            return -1;
        }

        // Load organization from gitprovider's repository
        Organization organization = organizationRepository.findById(metadata.organizationId()).orElse(null);
        if (organization == null) {
            log.warn("Organization {} not found in database", orgLogin);
            return 0;
        }

        log.info("Syncing issue types for organization {}", orgLogin);

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);

        try {
            Set<String> syncedIds = new HashSet<>();
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;
            int pageCount = 0;

            while (hasNextPage) {
                if (pageCount >= MAX_PAGINATION_PAGES) {
                    log.warn(
                        "Reached maximum pagination limit ({}) for organization {}, stopping",
                        MAX_PAGINATION_PAGES,
                        orgLogin
                    );
                    break;
                }
                pageCount++;

                IssueTypeConnection response = client
                    .documentName(GET_ISSUE_TYPES_DOCUMENT)
                    .variable("login", orgLogin)
                    .variable("first", LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("organization.issueTypes")
                    .toEntity(IssueTypeConnection.class)
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || response.getNodes() == null) {
                    break;
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
            }

            removeDeletedIssueTypes(organization.getId(), syncedIds);
            syncTargetProvider.updateWorkspaceSyncTimestamp(
                workspaceId,
                SyncTargetProvider.SyncType.ISSUE_TYPES,
                Instant.now()
            );

            log.info("Synced {} issue types for organization {}", totalSynced, orgLogin);
            return totalSynced;
        } catch (Exception e) {
            log.error("Error syncing issue types for organization {}: {}", orgLogin, e.getMessage(), e);
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
                log.debug("Removed deleted issue type: {}", existingType.getName());
            }
        }
    }

    private void syncIssueType(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.IssueType graphQlType,
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

    private IssueType.Color convertColor(IssueTypeColor graphQlColor) {
        if (graphQlColor == null) {
            return IssueType.Color.GRAY;
        }
        try {
            return IssueType.Color.valueOf(graphQlColor.name());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown issue type color '{}', using GRAY as fallback", graphQlColor);
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
            log.warn("Unknown issue type color '{}', using GRAY as fallback", colorString);
            return IssueType.Color.GRAY;
        }
    }
}
