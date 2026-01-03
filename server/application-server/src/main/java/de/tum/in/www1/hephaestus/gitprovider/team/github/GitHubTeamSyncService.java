package de.tum.in.www1.hephaestus.gitprovider.team.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.*;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.RepositoryPermission;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.TeamConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.TeamMemberConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.TeamMemberEdge;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.TeamMemberRole;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.TeamPrivacy;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.TeamRepositoryConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.TeamRepositoryEdge;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.User;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.github.dto.GitHubTeamEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembership;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.graphql.client.HttpGraphQlClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for synchronizing GitHub teams via GraphQL API.
 * <p>
 * This service fetches teams for an organization via GraphQL and delegates to
 * {@link GitHubTeamProcessor} for persistence, ensuring a single source of truth
 * for team processing logic.
 * <p>
 * It also handles synchronization of team memberships by creating
 * {@link TeamMembership} records for each member returned by the GraphQL query.
 */
@Service
public class GitHubTeamSyncService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubTeamSyncService.class);
    private static final String GET_ORGANIZATION_TEAMS_DOCUMENT = "GetOrganizationTeams";
    private static final String GET_TEAM_MEMBERS_DOCUMENT = "GetTeamMembers";
    private static final String GET_TEAM_REPOSITORIES_DOCUMENT = "GetTeamRepositories";

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final RepositoryRepository repositoryRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubTeamProcessor teamProcessor;
    private final GitHubUserProcessor userProcessor;

    public GitHubTeamSyncService(
        TeamRepository teamRepository,
        TeamMembershipRepository teamMembershipRepository,
        RepositoryRepository repositoryRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubTeamProcessor teamProcessor,
        GitHubUserProcessor userProcessor
    ) {
        this.teamRepository = teamRepository;
        this.teamMembershipRepository = teamMembershipRepository;
        this.repositoryRepository = repositoryRepository;
        this.graphQlClientProvider = graphQlClientProvider;
        this.teamProcessor = teamProcessor;
        this.userProcessor = userProcessor;
    }

    /**
     * Synchronizes all teams for an organization using GraphQL.
     * <p>
     * This method fetches all teams from the organization, processes them using
     * {@link GitHubTeamProcessor}, and also synchronizes team memberships.
     *
     * @param workspaceId       the workspace ID for authentication
     * @param organizationLogin the GitHub organization login to sync teams for
     * @return number of teams synced
     */
    @Transactional
    public int syncTeamsForOrganization(Long workspaceId, String organizationLogin) {
        if (organizationLogin == null || organizationLogin.isBlank()) {
            logger.warn("Organization login is null or blank, cannot sync teams");
            return 0;
        }

        HttpGraphQlClient client = graphQlClientProvider.forWorkspace(workspaceId);

        try {
            Set<Long> syncedTeamIds = new HashSet<>();
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;

            while (hasNextPage) {
                TeamConnection response = client
                    .documentName(GET_ORGANIZATION_TEAMS_DOCUMENT)
                    .variable("login", organizationLogin)
                    .variable("first", LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("organization.teams")
                    .toEntity(TeamConnection.class)
                    .block(GRAPHQL_TIMEOUT);

                if (response == null || response.getNodes() == null) {
                    break;
                }

                for (var graphQlTeam : response.getNodes()) {
                    Team team = processTeam(graphQlTeam, organizationLogin);
                    if (team != null) {
                        syncedTeamIds.add(team.getId());
                        syncTeamMemberships(client, team, graphQlTeam, organizationLogin);
                        syncTeamRepoPermissions(client, team, graphQlTeam, organizationLogin);
                        totalSynced++;
                    }
                }

                var pageInfo = response.getPageInfo();
                hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
                cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
            }

            // Remove teams that no longer exist in the organization
            removeDeletedTeams(organizationLogin, syncedTeamIds);

            logger.info("Synced {} teams for organization {}", totalSynced, organizationLogin);
            return totalSynced;
        } catch (Exception e) {
            logger.error("Error syncing teams for organization {}: {}", organizationLogin, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Processes a single GraphQL team and persists it.
     *
     * @param graphQlTeam       the GraphQL team object
     * @param organizationLogin the organization login
     * @return the persisted Team entity, or null if processing failed
     */
    private Team processTeam(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Team graphQlTeam,
        String organizationLogin
    ) {
        GitHubTeamEventDTO.GitHubTeamDTO dto = convertToDTO(graphQlTeam);
        Team team = teamProcessor.process(dto, organizationLogin);

        if (team != null) {
            // Update parent team reference if available
            if (graphQlTeam.getParentTeam() != null && graphQlTeam.getParentTeam().getDatabaseId() != null) {
                team.setParentId(graphQlTeam.getParentTeam().getDatabaseId().longValue());
            }
            // Update last synced timestamp
            team.setLastSyncedAt(Instant.now());
            team = teamRepository.save(team);
        }

        return team;
    }

    /**
     * Synchronizes team memberships from the GraphQL response.
     * <p>
     * For each member in the team's members connection, this method ensures the
     * user exists and creates or updates a TeamMembership record with the correct role.
     * If the team has more than 100 members, this method paginates through all members
     * using the GetTeamMembers query.
     *
     * @param client          the GraphQL client for additional queries
     * @param team            the Team entity
     * @param graphQlTeam     the GraphQL team object containing members
     * @param organizationLogin the organization login for pagination queries
     */
    private void syncTeamMemberships(
        HttpGraphQlClient client,
        Team team,
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Team graphQlTeam,
        String organizationLogin
    ) {
        var membersConnection = graphQlTeam.getMembers();
        if (membersConnection == null || membersConnection.getEdges() == null) {
            return;
        }

        // Collect all member edges (with roles) - paginate if needed
        List<TeamMemberEdge> allMemberEdges = new ArrayList<>(membersConnection.getEdges());
        var membersPageInfo = membersConnection.getPageInfo();

        if (membersPageInfo != null && Boolean.TRUE.equals(membersPageInfo.getHasNextPage())) {
            logger.debug(
                "Team {} has more than 100 members (totalCount={}), fetching additional pages",
                team.getName(),
                membersConnection.getTotalCount()
            );
            List<TeamMemberEdge> additionalMemberEdges = fetchAllTeamMemberEdges(
                client,
                organizationLogin,
                graphQlTeam.getSlug(),
                membersPageInfo.getEndCursor()
            );
            allMemberEdges.addAll(additionalMemberEdges);
        }

        // Build existing memberships map for efficient lookup and role update
        Map<Long, TeamMembership> existingMemberships = team
            .getMemberships()
            .stream()
            .collect(Collectors.toMap(tm -> tm.getUser().getId(), tm -> tm));

        Set<Long> syncedMemberIds = new HashSet<>();

        for (var memberEdge : allMemberEdges) {
            if (memberEdge == null || memberEdge.getNode() == null || memberEdge.getNode().getDatabaseId() == null) {
                continue;
            }

            User graphQlUser = memberEdge.getNode();
            TeamMemberRole graphQlRole = memberEdge.getRole();

            // Convert GraphQL role to TeamMembership.Role
            TeamMembership.Role role = (graphQlRole == TeamMemberRole.MAINTAINER)
                ? TeamMembership.Role.MAINTAINER
                : TeamMembership.Role.MEMBER;

            // Convert GraphQL User to GitHubUserDTO and ensure user exists
            GitHubUserDTO userDTO = convertUserToDTO(graphQlUser);
            de.tum.in.www1.hephaestus.gitprovider.user.User user = userProcessor.ensureExists(userDTO);

            if (user != null) {
                syncedMemberIds.add(user.getId());

                TeamMembership existingMembership = existingMemberships.get(user.getId());
                if (existingMembership != null) {
                    // Update role if changed
                    if (existingMembership.getRole() != role) {
                        logger.info(
                            "Updating role for user {} in team {} from {} to {}",
                            sanitizeForLog(user.getLogin()),
                            sanitizeForLog(team.getName()),
                            existingMembership.getRole(),
                            role
                        );
                        existingMembership.setRole(role);
                        teamMembershipRepository.save(existingMembership);
                    }
                } else {
                    // Create new membership
                    TeamMembership membership = new TeamMembership(team, user, role);
                    teamMembershipRepository.save(membership);
                    logger.debug(
                        "Created membership for user {} in team {} with role {}",
                        sanitizeForLog(user.getLogin()),
                        sanitizeForLog(team.getName()),
                        role
                    );
                }
            }
        }

        // Remove memberships for users no longer in the team
        removeStaleTeamMemberships(team, syncedMemberIds);
    }

    /**
     * Synchronizes team repository permissions from the GraphQL response.
     * <p>
     * For each repository in the team's repositories connection, this method creates
     * or updates TeamRepositoryPermission records with the correct permission level.
     *
     * @param client          the GraphQL client for additional queries
     * @param team            the Team entity
     * @param graphQlTeam     the GraphQL team object containing repositories
     * @param organizationLogin the organization login for pagination queries
     */
    private void syncTeamRepoPermissions(
        HttpGraphQlClient client,
        Team team,
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Team graphQlTeam,
        String organizationLogin
    ) {
        var reposConnection = graphQlTeam.getRepositories();
        if (reposConnection == null || reposConnection.getEdges() == null) {
            return;
        }

        // Collect all repository edges - paginate if needed
        List<TeamRepositoryEdge> allRepoEdges = new ArrayList<>(reposConnection.getEdges());
        var reposPageInfo = reposConnection.getPageInfo();

        if (reposPageInfo != null && Boolean.TRUE.equals(reposPageInfo.getHasNextPage())) {
            logger.debug(
                "Team {} has more than 100 repositories (totalCount={}), fetching additional pages",
                team.getName(),
                reposConnection.getTotalCount()
            );
            List<TeamRepositoryEdge> additionalRepoEdges = fetchAllTeamRepositoryEdges(
                client,
                organizationLogin,
                graphQlTeam.getSlug(),
                reposPageInfo.getEndCursor()
            );
            allRepoEdges.addAll(additionalRepoEdges);
        }

        Set<TeamRepositoryPermission> freshPermissions = new HashSet<>();

        for (var repoEdge : allRepoEdges) {
            if (repoEdge == null || repoEdge.getNode() == null || repoEdge.getNode().getDatabaseId() == null) {
                continue;
            }

            Long repoId = repoEdge.getNode().getDatabaseId().longValue();

            // Skip unknown repos (not monitored by this workspace)
            if (!repositoryRepository.existsById(repoId)) {
                continue;
            }

            Repository repoRef = repositoryRepository.getReferenceById(repoId);
            TeamRepositoryPermission.PermissionLevel level = convertPermission(repoEdge.getPermission());

            // Find existing permission or create new
            TeamRepositoryPermission permission = team
                .getRepoPermissions()
                .stream()
                .filter(existing -> Objects.equals(existing.getRepository().getId(), repoId))
                .findFirst()
                .orElseGet(() -> new TeamRepositoryPermission(team, repoRef, level));

            permission.setPermission(level);
            freshPermissions.add(permission);
        }

        team.clearAndAddRepoPermissions(freshPermissions);
        logger.debug(
            "Synced {} repository permissions for team {}",
            freshPermissions.size(),
            sanitizeForLog(team.getName())
        );
    }

    /**
     * Converts GraphQL RepositoryPermission to TeamRepositoryPermission.PermissionLevel.
     */
    private TeamRepositoryPermission.PermissionLevel convertPermission(RepositoryPermission graphQlPermission) {
        if (graphQlPermission == null) {
            return TeamRepositoryPermission.PermissionLevel.READ;
        }
        return switch (graphQlPermission) {
            case ADMIN -> TeamRepositoryPermission.PermissionLevel.ADMIN;
            case MAINTAIN -> TeamRepositoryPermission.PermissionLevel.MAINTAIN;
            case WRITE -> TeamRepositoryPermission.PermissionLevel.WRITE;
            case TRIAGE -> TeamRepositoryPermission.PermissionLevel.TRIAGE;
            case READ -> TeamRepositoryPermission.PermissionLevel.READ;
        };
    }

    /**
     * Fetches all remaining team member edges (with roles) using pagination.
     *
     * @param client            the GraphQL client
     * @param organizationLogin the organization login
     * @param teamSlug          the team slug
     * @param startCursor       the cursor to start from
     * @return list of all remaining member edges with roles
     */
    private List<TeamMemberEdge> fetchAllTeamMemberEdges(
        HttpGraphQlClient client,
        String organizationLogin,
        String teamSlug,
        String startCursor
    ) {
        List<TeamMemberEdge> allMemberEdges = new ArrayList<>();
        String cursor = startCursor;
        boolean hasNextPage = true;

        while (hasNextPage) {
            TeamMemberConnection response = client
                .documentName(GET_TEAM_MEMBERS_DOCUMENT)
                .variable("orgLogin", organizationLogin)
                .variable("teamSlug", teamSlug)
                .variable("first", LARGE_PAGE_SIZE)
                .variable("after", cursor)
                .retrieve("organization.team.members")
                .toEntity(TeamMemberConnection.class)
                .block(GRAPHQL_TIMEOUT);

            if (response == null || response.getEdges() == null) {
                break;
            }

            allMemberEdges.addAll(response.getEdges());

            var pageInfo = response.getPageInfo();
            hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
            cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
        }

        return allMemberEdges;
    }

    /**
     * Fetches all remaining team repository edges (with permissions) using pagination.
     *
     * @param client            the GraphQL client
     * @param organizationLogin the organization login
     * @param teamSlug          the team slug
     * @param startCursor       the cursor to start from
     * @return list of all remaining repository edges with permissions
     */
    private List<TeamRepositoryEdge> fetchAllTeamRepositoryEdges(
        HttpGraphQlClient client,
        String organizationLogin,
        String teamSlug,
        String startCursor
    ) {
        List<TeamRepositoryEdge> allEdges = new ArrayList<>();
        String cursor = startCursor;
        boolean hasNextPage = true;

        while (hasNextPage) {
            TeamRepositoryConnection response = client
                .documentName(GET_TEAM_REPOSITORIES_DOCUMENT)
                .variable("orgLogin", organizationLogin)
                .variable("teamSlug", teamSlug)
                .variable("first", LARGE_PAGE_SIZE)
                .variable("after", cursor)
                .retrieve("organization.team.repositories")
                .toEntity(TeamRepositoryConnection.class)
                .block(GRAPHQL_TIMEOUT);

            if (response == null || response.getEdges() == null) {
                break;
            }

            allEdges.addAll(response.getEdges());

            var pageInfo = response.getPageInfo();
            hasNextPage = pageInfo != null && Boolean.TRUE.equals(pageInfo.getHasNextPage());
            cursor = pageInfo != null ? pageInfo.getEndCursor() : null;
        }

        return allEdges;
    }

    /**
     * Removes team memberships for users who are no longer members of the team.
     *
     * @param team            the Team entity
     * @param syncedMemberIds the set of user IDs that are still members
     */
    private void removeStaleTeamMemberships(Team team, Set<Long> syncedMemberIds) {
        Set<TeamMembership> currentMemberships = team.getMemberships();
        int removed = 0;

        for (TeamMembership membership : new HashSet<>(currentMemberships)) {
            if (!syncedMemberIds.contains(membership.getUser().getId())) {
                teamMembershipRepository.delete(membership);
                team.removeMembership(membership);
                removed++;
            }
        }

        if (removed > 0) {
            logger.debug("Removed {} stale memberships from team {}", removed, team.getName());
        }
    }

    /**
     * Removes teams that no longer exist in the organization.
     *
     * @param organizationLogin the organization login
     * @param syncedTeamIds     the set of team IDs that were synced
     */
    private void removeDeletedTeams(String organizationLogin, Set<Long> syncedTeamIds) {
        List<Team> existingTeams = teamRepository.findAllByOrganizationIgnoreCase(organizationLogin);
        int removed = 0;

        for (Team team : existingTeams) {
            if (!syncedTeamIds.contains(team.getId())) {
                teamProcessor.delete(team.getId());
                removed++;
            }
        }

        if (removed > 0) {
            logger.info("Deleted {} stale teams for organization {}", removed, organizationLogin);
        }
    }

    /**
     * Converts a GraphQL Team to a GitHubTeamDTO.
     *
     * @param graphQlTeam the GraphQL team object
     * @return the DTO for use with GitHubTeamProcessor
     */
    private GitHubTeamEventDTO.GitHubTeamDTO convertToDTO(
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Team graphQlTeam
    ) {
        Long databaseId = graphQlTeam.getDatabaseId() != null ? graphQlTeam.getDatabaseId().longValue() : null;

        String privacy = mapPrivacy(graphQlTeam.getPrivacy());
        String htmlUrl = graphQlTeam.getUrl() != null ? graphQlTeam.getUrl().toString() : null;

        return new GitHubTeamEventDTO.GitHubTeamDTO(
            databaseId,
            graphQlTeam.getId(),
            graphQlTeam.getName(),
            graphQlTeam.getSlug(),
            graphQlTeam.getDescription(),
            privacy,
            null, // permission - not available in team query
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
            null // email - not fetched in the query
        );
    }

    /**
     * Maps GraphQL TeamPrivacy enum to string for DTO.
     *
     * @param privacy the GraphQL TeamPrivacy enum value
     * @return the privacy string, or null if privacy is null
     */
    private String mapPrivacy(TeamPrivacy privacy) {
        if (privacy == null) {
            return null;
        }
        return switch (privacy) {
            case SECRET -> "secret";
            case VISIBLE -> "closed"; // VISIBLE maps to closed in the REST API
        };
    }
}
