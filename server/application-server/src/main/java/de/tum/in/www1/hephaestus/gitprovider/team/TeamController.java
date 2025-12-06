package de.tum.in.www1.hephaestus.gitprovider.team;

import de.tum.in.www1.hephaestus.gitprovider.team.permission.TeamRepositoryPermissionRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContextResolver;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for managing teams within a workspace.
 * Teams are synchronized from the GitHub organization and can be configured
 * to filter leaderboard and activity data.
 */
@WorkspaceScopedController
@RequestMapping("/team")
@Tag(name = "Teams", description = "Team management within workspace")
public class TeamController {

    private static final Logger logger = LoggerFactory.getLogger(TeamController.class);

    private final TeamRepository teamRepo;
    private final TeamInfoDTOConverter converter;
    private final TeamRepositoryPermissionRepository permissionRepository;
    private final WorkspaceContextResolver workspaceResolver;

    public TeamController(
        TeamRepository teamRepo,
        TeamInfoDTOConverter converter,
        TeamRepositoryPermissionRepository permissionRepository,
        WorkspaceContextResolver workspaceResolver
    ) {
        this.teamRepo = teamRepo;
        this.converter = converter;
        this.permissionRepository = permissionRepository;
        this.workspaceResolver = workspaceResolver;
    }

    /**
     * Get all teams in the workspace.
     *
     * @param workspaceContext the resolved workspace context
     * @return list of teams with their members and permissions
     */
    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "List teams", description = "Returns all teams in the workspace organization")
    public ResponseEntity<List<TeamInfoDTO>> getAllTeams(WorkspaceContext workspaceContext) {
        logger.info("Listing teams for workspace {}", workspaceContext.slug());
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        if (workspace.getAccountLogin() == null) {
            return ResponseEntity.ok(List.of());
        }

        List<TeamInfoDTO> teams = teamRepo
            .findWithCollectionsByOrganizationIgnoreCase(workspace.getAccountLogin())
            .stream()
            .map(converter::convert)
            .toList();
        return ResponseEntity.ok(teams);
    }

    /**
     * Update team visibility in the leaderboard.
     * Hidden teams are excluded from leaderboard calculations.
     *
     * @param workspaceContext the resolved workspace context
     * @param id the team ID
     * @param hidden whether to hide the team (body parameter, preferred)
     * @param hiddenParam whether to hide the team (query parameter, fallback)
     * @return 200 OK on success, 404 if team not found
     */
    @PostMapping("/{id}/visibility")
    @RequireAtLeastWorkspaceAdmin
    @Operation(summary = "Update team visibility", description = "Show or hide a team in leaderboard calculations")
    public ResponseEntity<Void> updateTeamVisibility(
        WorkspaceContext workspaceContext,
        @PathVariable Long id,
        @RequestBody(required = false) Boolean hidden,
        @RequestParam(name = "hidden", required = false) Boolean hiddenParam
    ) {
        logger.info("Updating team {} visibility in workspace {}", id, workspaceContext.slug());
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        // Accept hidden flag from body (preferred) or from query parameter as fallback
        final var resolvedHidden = hidden != null ? hidden : hiddenParam;
        return teamRepo
            .findById(id)
            .filter(team -> belongsToWorkspace(team, workspace))
            .map(team -> {
                team.setHidden(Boolean.TRUE.equals(resolvedHidden));
                teamRepo.save(team);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{teamId}/repositories/{repositoryId}/visibility")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<Void> updateRepositoryVisibility(
        WorkspaceContext workspaceContext,
        @PathVariable Long teamId,
        @PathVariable Long repositoryId,
        @RequestBody(required = false) Boolean hiddenFromContributions,
        @RequestParam(name = "hiddenFromContributions", required = false) Boolean hiddenFromContributionsParam
    ) {
        logger.info(
            "Updating repository {} visibility for team {} in workspace {}",
            repositoryId,
            teamId,
            workspaceContext.slug()
        );
        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);
        final var resolvedHidden = hiddenFromContributions != null
            ? hiddenFromContributions
            : hiddenFromContributionsParam;

        if (resolvedHidden == null) {
            return ResponseEntity.badRequest().build();
        }

        // Use findWithTeamBy... to eagerly load the team for the workspace check
        return permissionRepository
            .findWithTeamByTeam_IdAndRepository_Id(teamId, repositoryId)
            .filter(permission -> belongsToWorkspace(permission.getTeam(), workspace))
            .map(permission -> {
                permission.setHiddenFromContributions(Boolean.TRUE.equals(resolvedHidden));
                permissionRepository.save(permission);
                return ResponseEntity.ok().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }

    private boolean belongsToWorkspace(Team team, Workspace workspace) {
        if (team == null || workspace == null || workspace.getAccountLogin() == null) {
            return false;
        }
        return workspace.getAccountLogin().equalsIgnoreCase(team.getOrganization());
    }
}
