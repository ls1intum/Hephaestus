package de.tum.in.www1.hephaestus.gitprovider.organization.github;

import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.*;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.OrganizationMemberConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.OrganizationMemberEdge;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.OrganizationMemberRole;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.PageInfo;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.dto.GitHubOrganizationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub organizations via GraphQL API.
 * <p>
 * This service fetches organization data via GraphQL and delegates to
 * {@link GitHubOrganizationProcessor} for persistence, ensuring a single source of truth
 * for organization processing logic.
 * <p>
 * It also handles synchronization of organization memberships by upserting
 * membership records for each member returned by the GraphQL query.
 */
@Service
public class GitHubOrganizationSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubOrganizationSyncService.class);
    private static final String GET_ORGANIZATION_DOCUMENT = "GetOrganization";
    private static final String GET_ORGANIZATION_MEMBERS_DOCUMENT = "GetOrganizationMembers";

    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubOrganizationProcessor organizationProcessor;
    private final GitHubUserProcessor userProcessor;
    private final OrganizationMembershipRepository organizationMembershipRepository;

    public GitHubOrganizationSyncService(
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubOrganizationProcessor organizationProcessor,
        GitHubUserProcessor userProcessor,
        OrganizationMembershipRepository organizationMembershipRepository
    ) {
        this.graphQlClientProvider = graphQlClientProvider;
        this.organizationProcessor = organizationProcessor;
        this.userProcessor = userProcessor;
        this.organizationMembershipRepository = organizationMembershipRepository;
    }

    /**
     * Synchronizes an organization by login using GraphQL.
     * <p>
     * This method fetches the organization details from GitHub's GraphQL API,
     * processes it using {@link GitHubOrganizationProcessor}, and also synchronizes
     * organization memberships.
     *
     * @param workspaceId        the workspace ID for authentication
     * @param organizationLogin  the GitHub organization login to sync
     * @return the synchronized Organization entity, or null if sync failed
     */
    @Transactional
    public Organization syncOrganization(Long workspaceId, String organizationLogin) {
        if (organizationLogin == null || organizationLogin.isBlank()) {
            logger.warn("Organization login is null or blank, cannot sync");
            return null;
        }

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);

        try {
            de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Organization graphQlOrg = client
                .documentName(GET_ORGANIZATION_DOCUMENT)
                .variable("login", organizationLogin)
                .retrieve("organization")
                .toEntity(de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Organization.class)
                .block(GRAPHQL_TIMEOUT);

            if (graphQlOrg == null) {
                logger.warn("Organization not found via GraphQL: {}", organizationLogin);
                return null;
            }

            // Convert GraphQL response to DTO and process
            GitHubOrganizationEventDTO.GitHubOrganizationDTO dto = convertToDTO(graphQlOrg);
            Organization organization = organizationProcessor.process(dto);

            if (organization != null) {
                // Sync organization memberships with full pagination
                int membersSynced = syncOrganizationMemberships(client, organization, graphQlOrg);
                logger.info(
                    "Synced organization {} ({}) with {} members",
                    organization.getLogin(),
                    organization.getGithubId(),
                    membersSynced
                );
            }

            return organization;
        } catch (Exception e) {
            logger.error("Error syncing organization {}: {}", organizationLogin, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Synchronizes organization memberships from the GraphQL response with full pagination.
     * <p>
     * For each member in the organization's membersWithRole connection, this method
     * ensures the user exists and upserts an OrganizationMembership record.
     * If there are more than 100 members, it paginates through all pages.
     *
     * @param client       the GraphQL client for pagination requests
     * @param organization the Organization entity
     * @param graphQlOrg   the GraphQL organization object containing initial members
     * @return the number of members synced
     */
    private int syncOrganizationMemberships(
        HttpGraphQlClient client,
        Organization organization,
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Organization graphQlOrg
    ) {
        var membersConnection = graphQlOrg.getMembersWithRole();
        if (membersConnection == null || membersConnection.getEdges() == null) {
            logger.debug("No members found for organization {}", organization.getLogin());
            return 0;
        }

        // Collect all members with pagination
        List<OrganizationMemberEdge> allMembers = new ArrayList<>(membersConnection.getEdges());
        PageInfo pageInfo = membersConnection.getPageInfo();
        String cursor = pageInfo != null ? pageInfo.getEndCursor() : null;

        // Paginate through all remaining members if there are more pages
        while (pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage())) {
            OrganizationMemberConnection nextPage = client
                .documentName(GET_ORGANIZATION_MEMBERS_DOCUMENT)
                .variable("login", organization.getLogin())
                .variable("first", LARGE_PAGE_SIZE)
                .variable("after", cursor)
                .retrieve("organization.membersWithRole")
                .toEntity(OrganizationMemberConnection.class)
                .block(GRAPHQL_TIMEOUT);

            if (nextPage == null || nextPage.getEdges() == null) {
                break;
            }

            allMembers.addAll(nextPage.getEdges());
            pageInfo = nextPage.getPageInfo();
            cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
        }

        logger.debug(
            "Fetched {} total members for organization {} (totalCount={})",
            allMembers.size(),
            organization.getLogin(),
            membersConnection.getTotalCount()
        );

        Set<Long> syncedUserIds = new HashSet<>();
        int memberCount = 0;

        for (OrganizationMemberEdge edge : allMembers) {
            if (edge == null || edge.getNode() == null) {
                continue;
            }

            var graphQlUser = edge.getNode();
            if (graphQlUser.getDatabaseId() == null) {
                continue;
            }

            // Convert GraphQL User to GitHubUserDTO and ensure user exists
            GitHubUserDTO userDTO = convertUserToDTO(graphQlUser);
            User user = userProcessor.ensureExists(userDTO);

            if (user != null) {
                syncedUserIds.add(user.getId());

                // Get role from edge
                String role = mapRole(edge.getRole());

                // Upsert membership
                organizationMembershipRepository.upsertMembership(organization.getId(), user.getId(), role);
                memberCount++;
                logger.debug(
                    "Synced membership for user {} in organization {} with role {}",
                    user.getLogin(),
                    organization.getLogin(),
                    role
                );
            }
        }

        // Remove memberships for users no longer in the organization
        removeStaleMemberships(organization, syncedUserIds);

        return memberCount;
    }

    /**
     * Removes organization memberships for users who are no longer members.
     *
     * @param organization   the Organization entity
     * @param syncedUserIds  the set of user IDs that are still members
     */
    private void removeStaleMemberships(Organization organization, Set<Long> syncedUserIds) {
        List<Long> existingUserIds = organizationMembershipRepository.findUserIdsByOrganizationId(organization.getId());

        Set<Long> staleUserIds = new HashSet<>(existingUserIds);
        staleUserIds.removeAll(syncedUserIds);

        if (!staleUserIds.isEmpty()) {
            organizationMembershipRepository.deleteByOrganizationIdAndUserIdIn(organization.getId(), staleUserIds);
            logger.debug(
                "Removed {} stale memberships from organization {}",
                staleUserIds.size(),
                organization.getLogin()
            );
        }
    }

    /**
     * Converts a GraphQL Organization to a GitHubOrganizationDTO.
     *
     * @param graphQlOrg the GraphQL organization object
     * @return the DTO for use with GitHubOrganizationProcessor
     */
    private GitHubOrganizationEventDTO.GitHubOrganizationDTO convertToDTO(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Organization graphQlOrg
    ) {
        Long databaseId = graphQlOrg.getDatabaseId() != null ? graphQlOrg.getDatabaseId().longValue() : null;

        String avatarUrl = graphQlOrg.getAvatarUrl() != null ? graphQlOrg.getAvatarUrl().toString() : null;

        String htmlUrl = graphQlOrg.getUrl() != null ? graphQlOrg.getUrl().toString() : null;

        return new GitHubOrganizationEventDTO.GitHubOrganizationDTO(
            databaseId,
            graphQlOrg.getId(),
            graphQlOrg.getLogin(),
            graphQlOrg.getDescription(),
            avatarUrl,
            htmlUrl
        );
    }

    /**
     * Converts a GraphQL User to a GitHubUserDTO.
     *
     * @param graphQlUser the GraphQL user object
     * @return the DTO for use with GitHubUserProcessor
     */
    private GitHubUserDTO convertUserToDTO(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.User graphQlUser
    ) {
        Long databaseId = graphQlUser.getDatabaseId() != null ? graphQlUser.getDatabaseId().longValue() : null;

        String avatarUrl = graphQlUser.getAvatarUrl() != null ? graphQlUser.getAvatarUrl().toString() : null;

        return new GitHubUserDTO(
            databaseId,
            databaseId,
            graphQlUser.getLogin(),
            avatarUrl,
            null, // htmlUrl - not fetched in the query
            graphQlUser.getName(),
            graphQlUser.getEmail()
        );
    }

    /**
     * Maps GraphQL OrganizationMemberRole enum to string for storage.
     *
     * @param role the GraphQL OrganizationMemberRole enum value
     * @return the role string (uppercase), or "MEMBER" as default
     */
    private String mapRole(OrganizationMemberRole role) {
        if (role == null) {
            return "MEMBER";
        }
        return role.toString().toUpperCase();
    }
}
