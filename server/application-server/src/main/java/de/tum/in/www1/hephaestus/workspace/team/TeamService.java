package de.tum.in.www1.hephaestus.workspace.team;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTOConverter;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermission;
import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermissionRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing teams within a workspace.
 *
 * <p>Handles team-related business logic including:
 * <ul>
 *   <li>Retrieving teams for a workspace</li>
 *   <li>Updating team visibility in leaderboard</li>
 *   <li>Updating repository visibility for teams</li>
 * </ul>
 */
@Service
public class TeamService {

    private static final Logger log = LoggerFactory.getLogger(TeamService.class);

    private final TeamRepository teamRepository;
    private final TeamInfoDTOConverter converter;
    private final TeamRepositoryPermissionRepository permissionRepository;

    public TeamService(
        TeamRepository teamRepository,
        TeamInfoDTOConverter converter,
        TeamRepositoryPermissionRepository permissionRepository
    ) {
        this.teamRepository = teamRepository;
        this.converter = converter;
        this.permissionRepository = permissionRepository;
    }

    /**
     * Gets all teams in a workspace.
     *
     * @param workspace the workspace to get teams for
     * @return list of teams with their members and permissions, empty list if workspace has no account
     */
    @Transactional(readOnly = true)
    public List<TeamInfoDTO> getAllTeams(Workspace workspace) {
        if (workspace.getAccountLogin() == null) {
            return List.of();
        }

        return teamRepository
            .findWithCollectionsByOrganizationIgnoreCase(workspace.getAccountLogin())
            .stream()
            .map(converter::convert)
            .toList();
    }

    /**
     * Updates the visibility of a team in leaderboard calculations.
     *
     * @param workspace the workspace the team belongs to
     * @param teamId the ID of the team to update
     * @param hidden whether to hide the team from leaderboard
     * @return true if the team was updated, false if not found or not in workspace
     */
    @Transactional
    public boolean updateTeamVisibility(Workspace workspace, Long teamId, Boolean hidden) {
        log.info(
            "Updating team {} visibility to hidden={} in workspace {}",
            teamId,
            hidden,
            workspace.getWorkspaceSlug()
        );

        return teamRepository
            .findById(teamId)
            .filter(team -> belongsToWorkspace(team, workspace))
            .map(team -> {
                team.setHidden(Boolean.TRUE.equals(hidden));
                teamRepository.save(team);
                return true;
            })
            .orElse(false);
    }

    /**
     * Updates the visibility of a repository for a team's contributions.
     *
     * @param workspace the workspace the team belongs to
     * @param teamId the ID of the team
     * @param repositoryId the ID of the repository
     * @param hiddenFromContributions whether to hide the repository from contributions
     * @return true if the permission was updated, false if not found or not in workspace
     * @throws IllegalArgumentException if hiddenFromContributions is null
     */
    @Transactional
    public boolean updateRepositoryVisibility(
        Workspace workspace,
        Long teamId,
        Long repositoryId,
        Boolean hiddenFromContributions
    ) {
        log.info(
            "Updating repository {} visibility for team {} to hiddenFromContributions={} in workspace {}",
            repositoryId,
            teamId,
            hiddenFromContributions,
            workspace.getWorkspaceSlug()
        );

        if (hiddenFromContributions == null) {
            throw new IllegalArgumentException("hiddenFromContributions must not be null");
        }

        return permissionRepository
            .findWithTeamByTeam_IdAndRepository_Id(teamId, repositoryId)
            .filter(permission -> belongsToWorkspace(permission.getTeam(), workspace))
            .map(permission -> {
                permission.setHiddenFromContributions(Boolean.TRUE.equals(hiddenFromContributions));
                permissionRepository.save(permission);
                return true;
            })
            .orElse(false);
    }

    /**
     * Gets a team by ID, ensuring it belongs to the specified workspace.
     *
     * @param workspace the workspace the team must belong to
     * @param teamId the ID of the team
     * @return the team entity
     * @throws EntityNotFoundException if the team is not found or not in workspace
     */
    @Transactional(readOnly = true)
    public Team getTeam(Workspace workspace, Long teamId) {
        return teamRepository
            .findById(teamId)
            .filter(team -> belongsToWorkspace(team, workspace))
            .orElseThrow(() -> new EntityNotFoundException("Team", teamId));
    }

    /**
     * Gets a team repository permission, ensuring the team belongs to the specified workspace.
     *
     * @param workspace the workspace the team must belong to
     * @param teamId the ID of the team
     * @param repositoryId the ID of the repository
     * @return the permission entity
     * @throws EntityNotFoundException if the permission is not found or team not in workspace
     */
    @Transactional(readOnly = true)
    public TeamRepositoryPermission getTeamRepositoryPermission(Workspace workspace, Long teamId, Long repositoryId) {
        return permissionRepository
            .findWithTeamByTeam_IdAndRepository_Id(teamId, repositoryId)
            .filter(permission -> belongsToWorkspace(permission.getTeam(), workspace))
            .orElseThrow(() -> new EntityNotFoundException("TeamRepositoryPermission", teamId + "/" + repositoryId));
    }

    private boolean belongsToWorkspace(Team team, Workspace workspace) {
        if (team == null || workspace == null || workspace.getAccountLogin() == null) {
            return false;
        }
        return workspace.getAccountLogin().equalsIgnoreCase(team.getOrganization());
    }
}
