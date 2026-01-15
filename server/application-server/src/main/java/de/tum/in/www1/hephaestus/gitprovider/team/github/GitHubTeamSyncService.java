package de.tum.in.www1.hephaestus.gitprovider.team.github;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;
import static de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubSyncConstants.*;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHRepositoryPermission;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHTeam;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHTeamConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHTeamMemberConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHTeamMemberEdge;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHTeamMemberRole;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHTeamPrivacy;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHTeamRepositoryConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHTeamRepositoryEdge;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.GHUser;
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

    private static final Logger log = LoggerFactory.getLogger(GitHubTeamSyncService.class);
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
     * @param scopeId       the scope ID for authentication
     * @param organizationLogin the GitHub organization login to sync teams for
     * @return number of teams synced
     */
    @Transactional
    public int syncTeamsForOrganization(Long scopeId, String organizationLogin) {
        if (organizationLogin == null || organizationLogin.isBlank()) {
            log.warn("Skipped team sync due to null or blank org login: scopeId={}", scopeId);
            return 0;
        }
        String safeOrgLogin = sanitizeForLog(organizationLogin);

        HttpGraphQlClient client = graphQlClientProvider.forScope(scopeId);

        try {
            Set<Long> syncedTeamIds = new HashSet<>();
            int totalSynced = 0;
            String cursor = null;
            boolean hasNextPage = true;
            int pageCount = 0;

            while (hasNextPage) {
                pageCount++;
                if (pageCount >= MAX_PAGINATION_PAGES) {
                    log.warn(
                        "Reached maximum pagination limit for teams: orgLogin={}, limit={}",
                        safeOrgLogin,
                        MAX_PAGINATION_PAGES
                    );
                    break;
                }

                GHTeamConnection response = client
                    .documentName(GET_ORGANIZATION_TEAMS_DOCUMENT)
                    .variable("login", organizationLogin)
                    .variable("first", LARGE_PAGE_SIZE)
                    .variable("after", cursor)
                    .retrieve("organization.teams")
                    .toEntity(GHTeamConnection.class)
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

            log.info("Completed team sync: orgLogin={}, teamCount={}, scopeId={}", safeOrgLogin, totalSynced, scopeId);
            return totalSynced;
        } catch (Exception e) {
            log.error("Failed to sync teams: orgLogin={}, scopeId={}", safeOrgLogin, scopeId, e);
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
    private Team processTeam(GHTeam graphQlTeam, String organizationLogin) {
        GitHubTeamEventDTO.GitHubTeamDTO dto = convertToDTO(graphQlTeam);
        Team team = teamProcessor.process(dto, organizationLogin);

        if (team != null) {
            // Update parent team reference if available
            if (graphQlTeam.getParentTeam() != null && graphQlTeam.getParentTeam().getDatabaseId() != null) {
                team.setParentId(graphQlTeam.getParentTeam().getDatabaseId().longValue());
            }
            // Update last synced timestamp
            team.setLastSyncAt(Instant.now());
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
        GHTeam graphQlTeam,
        String organizationLogin
    ) {
        var membersConnection = graphQlTeam.getMembers();
        if (membersConnection == null || membersConnection.getEdges() == null) {
            return;
        }

        // Collect all member edges (with roles) - paginate if needed
        List<GHTeamMemberEdge> allMemberEdges = new ArrayList<>(membersConnection.getEdges());
        var membersPageInfo = membersConnection.getPageInfo();

        if (membersPageInfo != null && Boolean.TRUE.equals(membersPageInfo.getHasNextPage())) {
            log.debug(
                "Fetching additional team members: teamName={}, totalCount={}",
                team.getName(),
                membersConnection.getTotalCount()
            );
            List<GHTeamMemberEdge> additionalMemberEdges = fetchAllTeamMemberEdges(
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

            GHUser graphQlUser = memberEdge.getNode();
            GHTeamMemberRole graphQlRole = memberEdge.getRole();

            // Convert GraphQL role to TeamMembership.Role
            TeamMembership.Role role = (graphQlRole == GHTeamMemberRole.MAINTAINER)
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
                        log.debug(
                            "Updated team membership role: userLogin={}, teamName={}, oldRole={}, newRole={}",
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
                    log.debug(
                        "Created team membership: userLogin={}, teamName={}, role={}",
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
        GHTeam graphQlTeam,
        String organizationLogin
    ) {
        var reposConnection = graphQlTeam.getRepositories();
        if (reposConnection == null || reposConnection.getEdges() == null) {
            return;
        }

        // Collect all repository edges - paginate if needed
        List<GHTeamRepositoryEdge> allRepoEdges = new ArrayList<>(reposConnection.getEdges());
        var reposPageInfo = reposConnection.getPageInfo();

        if (reposPageInfo != null && Boolean.TRUE.equals(reposPageInfo.getHasNextPage())) {
            log.debug(
                "Fetching additional team repositories: teamName={}, totalCount={}",
                team.getName(),
                reposConnection.getTotalCount()
            );
            List<GHTeamRepositoryEdge> additionalRepoEdges = fetchAllTeamRepositoryEdges(
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

            // Skip unknown repos (not monitored by this scope)
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
        log.debug(
            "Synced team repository permissions: teamName={}, count={}",
            sanitizeForLog(team.getName()),
            freshPermissions.size()
        );
    }

    /**
     * Converts GraphQL GHRepositoryPermission to TeamRepositoryPermission.PermissionLevel.
     */
    private TeamRepositoryPermission.PermissionLevel convertPermission(GHRepositoryPermission graphQlPermission) {
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
    private List<GHTeamMemberEdge> fetchAllTeamMemberEdges(
        HttpGraphQlClient client,
        String organizationLogin,
        String teamSlug,
        String startCursor
    ) {
        List<GHTeamMemberEdge> allMemberEdges = new ArrayList<>();
        String cursor = startCursor;
        boolean hasNextPage = true;
        int pageCount = 0;

        while (hasNextPage) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for team members: teamSlug={}, limit={}",
                    teamSlug,
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            GHTeamMemberConnection response = client
                .documentName(GET_TEAM_MEMBERS_DOCUMENT)
                .variable("orgLogin", organizationLogin)
                .variable("teamSlug", teamSlug)
                .variable("first", LARGE_PAGE_SIZE)
                .variable("after", cursor)
                .retrieve("organization.team.members")
                .toEntity(GHTeamMemberConnection.class)
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
    private List<GHTeamRepositoryEdge> fetchAllTeamRepositoryEdges(
        HttpGraphQlClient client,
        String organizationLogin,
        String teamSlug,
        String startCursor
    ) {
        List<GHTeamRepositoryEdge> allEdges = new ArrayList<>();
        String cursor = startCursor;
        boolean hasNextPage = true;
        int pageCount = 0;

        while (hasNextPage) {
            pageCount++;
            if (pageCount >= MAX_PAGINATION_PAGES) {
                log.warn(
                    "Reached maximum pagination limit for team repositories: teamSlug={}, limit={}",
                    teamSlug,
                    MAX_PAGINATION_PAGES
                );
                break;
            }

            GHTeamRepositoryConnection response = client
                .documentName(GET_TEAM_REPOSITORIES_DOCUMENT)
                .variable("orgLogin", organizationLogin)
                .variable("teamSlug", teamSlug)
                .variable("first", LARGE_PAGE_SIZE)
                .variable("after", cursor)
                .retrieve("organization.team.repositories")
                .toEntity(GHTeamRepositoryConnection.class)
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
            log.debug("Removed stale team memberships: teamName={}, membershipCount={}", team.getName(), removed);
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
            log.info("Removed stale teams: orgLogin={}, teamCount={}", sanitizeForLog(organizationLogin), removed);
        }
    }

    /**
     * Converts a GraphQL Team to a GitHubTeamDTO.
     *
     * @param graphQlTeam the GraphQL team object
     * @return the DTO for use with GitHubTeamProcessor
     */
    private GitHubTeamEventDTO.GitHubTeamDTO convertToDTO(GHTeam graphQlTeam) {
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
    private GitHubUserDTO convertUserToDTO(GHUser graphQlUser) {
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
     * Maps GraphQL GHTeamPrivacy enum to string for DTO.
     *
     * @param privacy the GraphQL GHTeamPrivacy enum value
     * @return the privacy string, or null if privacy is null
     */
    private String mapPrivacy(GHTeamPrivacy privacy) {
        if (privacy == null) {
            return null;
        }
        return switch (privacy) {
            case SECRET -> "secret";
            case VISIBLE -> "visible";
        };
    }
}
