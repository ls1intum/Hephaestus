package de.tum.in.www1.hephaestus.gitprovider.issuetype.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.IssueTypeColor;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.IssueTypeConnection;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueType;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueTypeRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Duration;
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

    private static final Logger logger = LoggerFactory.getLogger(GitHubIssueTypeSyncService.class);
    private static final int GRAPHQL_PAGE_SIZE = 100;
    private static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(30);
    private static final String GET_ISSUE_TYPES_DOCUMENT = "GetOrganizationIssueTypes";

    private final IssueTypeRepository issueTypeRepository;
    private final WorkspaceRepository workspaceRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final int syncCooldownInMinutes;

    public GitHubIssueTypeSyncService(
        IssueTypeRepository issueTypeRepository,
        WorkspaceRepository workspaceRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        @Value("${monitoring.sync-cooldown-in-minutes}") int syncCooldownInMinutes
    ) {
        this.issueTypeRepository = issueTypeRepository;
        this.workspaceRepository = workspaceRepository;
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
        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            logger.warn("Workspace {} not found, cannot sync issue types", workspaceId);
            return 0;
        }

        Organization organization = workspace.getOrganization();
        if (organization == null) {
            logger.debug("Workspace {} has no organization, skipping issue type sync", workspaceId);
            return 0;
        }

        if (!shouldSync(workspace)) {
            logger.debug("Skipping issue type sync for workspace {} - cooldown active", workspaceId);
            return -1;
        }

        String orgLogin = organization.getLogin();
        logger.info("Syncing issue types for organization {}", orgLogin);

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);

        try {
            Set<String> syncedIds = new HashSet<>();
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;

            while (hasNextPage) {
                IssueTypeConnection response = client
                    .documentName(GET_ISSUE_TYPES_DOCUMENT)
                    .variable("login", orgLogin)
                    .variable("first", GRAPHQL_PAGE_SIZE)
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
            workspace.setIssueTypesSyncedAt(Instant.now());
            workspaceRepository.save(workspace);

            logger.info("Synced {} issue types for organization {}", totalSynced, orgLogin);
            return totalSynced;
        } catch (Exception e) {
            logger.error("Error syncing issue types for organization {}: {}", orgLogin, e.getMessage(), e);
            return 0;
        }
    }

    private boolean shouldSync(Workspace workspace) {
        if (workspace.getIssueTypesSyncedAt() == null) {
            return true;
        }
        var cooldownTime = Instant.now().minusSeconds(syncCooldownInMinutes * 60L);
        return workspace.getIssueTypesSyncedAt().isBefore(cooldownTime);
    }

    private void removeDeletedIssueTypes(Long organizationId, Set<String> syncedIds) {
        if (syncedIds.isEmpty()) {
            return;
        }
        List<IssueType> existingTypes = issueTypeRepository.findAllByOrganizationId(organizationId);
        for (IssueType existingType : existingTypes) {
            if (!syncedIds.contains(existingType.getId())) {
                issueTypeRepository.delete(existingType);
                logger.debug("Removed deleted issue type: {}", existingType.getName());
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
            logger.warn("Unknown issue type color '{}', using GRAY as fallback", graphQlColor);
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
            logger.warn("Unknown issue type color '{}', using GRAY as fallback", colorString);
            return IssueType.Color.GRAY;
        }
    }
}
