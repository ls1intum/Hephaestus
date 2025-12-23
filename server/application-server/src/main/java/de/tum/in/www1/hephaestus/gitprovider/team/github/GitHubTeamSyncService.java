package de.tum.in.www1.hephaestus.gitprovider.team.github;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubGraphQlClientProvider;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.TeamConnection;
import de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.TeamPrivacy;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.github.dto.GitHubTeamEventDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembership;
import de.tum.in.www1.hephaestus.gitprovider.team.membership.TeamMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserProcessor;
import de.tum.in.www1.hephaestus.gitprovider.user.github.dto.GitHubUserDTO;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private static final int GRAPHQL_PAGE_SIZE = 100;
    private static final Duration GRAPHQL_TIMEOUT = Duration.ofSeconds(30);
    private static final String GET_ORGANIZATION_TEAMS_DOCUMENT = "GetOrganizationTeams";

    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final GitHubGraphQlClientProvider graphQlClientProvider;
    private final GitHubTeamProcessor teamProcessor;
    private final GitHubUserProcessor userProcessor;

    public GitHubTeamSyncService(
        TeamRepository teamRepository,
        TeamMembershipRepository teamMembershipRepository,
        GitHubGraphQlClientProvider graphQlClientProvider,
        GitHubTeamProcessor teamProcessor,
        GitHubUserProcessor userProcessor
    ) {
        this.teamRepository = teamRepository;
        this.teamMembershipRepository = teamMembershipRepository;
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
                    .variable("first", GRAPHQL_PAGE_SIZE)
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
                        syncTeamMemberships(team, graphQlTeam);
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
     * user exists and creates a TeamMembership record if one doesn't already exist.
     *
     * @param team      the Team entity
     * @param graphQlTeam the GraphQL team object containing members
     */
    private void syncTeamMemberships(
        Team team,
        de.tum.in.www1.hephaestus.gitprovider.graphql.github.model.Team graphQlTeam
    ) {
        var membersConnection = graphQlTeam.getMembers();
        if (membersConnection == null || membersConnection.getNodes() == null) {
            return;
        }

        Set<Long> syncedMemberIds = new HashSet<>();

        for (var graphQlUser : membersConnection.getNodes()) {
            if (graphQlUser == null || graphQlUser.getDatabaseId() == null) {
                continue;
            }

            // Convert GraphQL User to GitHubUserDTO and ensure user exists
            GitHubUserDTO userDTO = convertUserToDTO(graphQlUser);
            User user = userProcessor.ensureExists(userDTO);

            if (user != null) {
                syncedMemberIds.add(user.getId());

                // Create membership if it doesn't exist
                if (!teamMembershipRepository.existsByTeam_IdAndUser_Id(team.getId(), user.getId())) {
                    TeamMembership membership = new TeamMembership(team, user, TeamMembership.Role.MEMBER);
                    teamMembershipRepository.save(membership);
                    logger.debug("Created membership for user {} in team {}", user.getLogin(), team.getName());
                }
            }
        }

        // Remove memberships for users no longer in the team
        removeStaleTeamMemberships(team, syncedMemberIds);
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
