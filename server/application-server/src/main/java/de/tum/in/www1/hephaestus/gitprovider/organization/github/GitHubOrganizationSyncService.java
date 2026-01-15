package de.tum.in.www1.hephaestus.gitprovider.organization.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.GRAPHQL_TIMEOUT;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.LARGE_PAGE_SIZE;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.MAX_PAGINATION_PAGES;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHOrganization;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHOrganizationMemberConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHOrganizationMemberEdge;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHOrganizationMemberRole;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHPageInfo;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHUser;
import de.tum.in.www1.hephaestus.gitprovider.organization.Organization;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMemberRole;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.organization.github.dto.GitHubOrganizationEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;
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

    private static final Logger log = LoggerFactory.getLogger(GitHubOrganizationSyncService.class);
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
     * @param scopeId        the scope ID for authentication
     * @param organizationLogin  the GitHub organization login to sync
     * @return the synchronized Organization entity, or null if sync failed
     */
    @Transactional
    public Organization syncOrganization(Long scopeId, String organizationLogin) {
        if (organizationLogin == null || organizationLogin.isBlank()) {
            log.warn("Skipped organization sync: reason=missing login, scopeId={}", scopeId);
            return null;
        }

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

        try {
            GHOrganization graphQlOrg = client
                .documentName(GET_ORGANIZATION_DOCUMENT)
                .variable("login", organizationLogin)
                .retrieve("organization")
                .toEntity(GHOrganization.class)
                .block(GRAPHQL_TIMEOUT);

            if (graphQlOrg == null) {
                log.warn("Skipped organization sync: reason=not found, orgLogin={}", sanitizeForLog(organizationLogin));
                return null;
            }

            // Convert GraphQL response to DTO and process
            GitHubOrganizationEventDTO.GitHubOrganizationDTO dto = convertToDTO(graphQlOrg);
            Organization organization = organizationProcessor.process(dto);

            if (organization != null) {
                // Sync organization memberships with full pagination
                int membersSynced = syncOrganizationMemberships(client, organization, graphQlOrg);

                // Mark sync timestamp - organization is managed entity, will be persisted at commit
                organization.setLastSyncAt(Instant.now());

                log.info(
                    "Synced organization: orgId={}, orgLogin={}, memberCount={}",
                    organization.getGithubId(),
                    sanitizeForLog(organization.getLogin()),
                    membersSynced
                );
            }

            return organization;
        } catch (Exception e) {
            log.error("Failed to sync organization: orgLogin={}, scopeId={}", sanitizeForLog(organizationLogin), scopeId, e);
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
        GHOrganization graphQlOrg
    ) {
        var membersConnection = graphQlOrg.getMembersWithRole();
        if (membersConnection == null || membersConnection.getEdges() == null) {
            log.debug("No members found for organization: orgId={}, orgLogin={}", organization.getGithubId(), sanitizeForLog(organization.getLogin()));
            return 0;
        }

        // Collect all members with pagination
        List<GHOrganizationMemberEdge> allMembers = new ArrayList<>(membersConnection.getEdges());
        GHPageInfo pageInfo = membersConnection.getPageInfo();
        String cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
        int pageCount = 0;

        // Paginate through all remaining members if there are more pages
        while (pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage())) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for organization members: orgLogin={}, limit={}",
                    sanitizeForLog(organization.getLogin()),
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            GHOrganizationMemberConnection nextPage = client
                .documentName(GET_ORGANIZATION_MEMBERS_DOCUMENT)
                .variable("login", organization.getLogin())
                .variable("first", LARGE_PAGE_SIZE)
                .variable("after", cursor)
                .retrieve("organization.membersWithRole")
                .toEntity(GHOrganizationMemberConnection.class)
                .block(GRAPHQL_TIMEOUT);

            if (nextPage == null || nextPage.getEdges() == null) {
                break;
            }

            allMembers.addAll(nextPage.getEdges());
            pageInfo = nextPage.getPageInfo();
            cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
        }

        log.debug(
            "Fetched organization members: orgId={}, orgLogin={}, fetchedCount={}, totalCount={}",
            organization.getGithubId(),
            sanitizeForLog(organization.getLogin()),
            allMembers.size(),
            membersConnection.getTotalCount()
        );

        Set<Long> syncedUserIds = new HashSet<>();
        int memberCount = 0;

        for (GHOrganizationMemberEdge edge : allMembers) {
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
                OrganizationMemberRole role = mapRole(edge.getRole());

                // Upsert membership
                organizationMembershipRepository.upsertMembership(organization.getId(), user.getId(), role);
                memberCount++;
                log.debug(
                    "Synced organization membership: orgId={}, userId={}, userLogin={}, role={}",
                    organization.getGithubId(),
                    user.getId(),
                    sanitizeForLog(user.getLogin()),
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
            log.debug(
                "Removed stale organization memberships: orgId={}, orgLogin={}, removedCount={}",
                organization.getGithubId(),
                sanitizeForLog(organization.getLogin()),
                staleUserIds.size()
            );
        }
    }

    /**
     * Converts a GraphQL Organization to a GitHubOrganizationDTO.
     *
     * @param graphQlOrg the GraphQL organization object
     * @return the DTO for use with GitHubOrganizationProcessor
     */
    private GitHubOrganizationEventDTO.GitHubOrganizationDTO convertToDTO(GHOrganization graphQlOrg) {
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
    private GitHubUserDTO convertUserToDTO(GHUser graphQlUser) {
        Long databaseId = graphQlUser.getDatabaseId() != null ? graphQlUser.getDatabaseId().longValue() : null;

        String avatarUrl = graphQlUser.getAvatarUrl() != null ? graphQlUser.getAvatarUrl().toString() : null;

        // Follow same pattern as GitHubUserDTO.fromUser(): null for 'id', databaseId for 'databaseId'
        // The 'id' field is only populated from REST webhook responses, not GraphQL
        return new GitHubUserDTO(
            null,
            databaseId,
            graphQlUser.getLogin(),
            avatarUrl,
            null, // htmlUrl - not fetched in the query
            graphQlUser.getName(),
            graphQlUser.getEmail()
        );
    }

    /**
     * Maps GraphQL OrganizationMemberRole enum to our domain enum.
     * <p>
     * Uses if-else instead of switch expression to avoid anonymous class generation
     * that causes NoClassDefFoundError with Spring Boot DevTools hot reload.
     *
     * @param graphQlRole the GraphQL OrganizationMemberRole enum value
     * @return the domain OrganizationMemberRole, or MEMBER as default
     */
    private OrganizationMemberRole mapRole(GHOrganizationMemberRole graphQlRole) {
        if (graphQlRole == null) {
            return OrganizationMemberRole.MEMBER;
        }
        if (graphQlRole == GHOrganizationMemberRole.ADMIN) {
            return OrganizationMemberRole.ADMIN;
        }
        return OrganizationMemberRole.MEMBER;
    }
}
