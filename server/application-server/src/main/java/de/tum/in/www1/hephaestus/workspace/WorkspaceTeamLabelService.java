package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.LoggingUtils;
import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserTeamsDTO;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.settings.WorkspaceTeamSettingsService;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for managing team and label associations within workspaces.
 *
 * <p>Provides business logic for:
 * <ul>
 *   <li>Retrieving users with their team memberships</li>
 *   <li>Adding and removing label filters for teams</li>
 * </ul>
 *
 * <p>This service works in conjunction with {@link WorkspaceTeamSettingsService}
 * for workspace-scoped team settings.
 *
 * @see WorkspaceTeamSettingsService
 * @see WorkspaceService
 */
@Service
@WorkspaceAgnostic("Manages team labels across workspaces - operates on workspace context, not within workspace scope")
public class WorkspaceTeamLabelService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceTeamLabelService.class);

    private final WorkspaceRepository workspaceRepository;
    private final TeamRepository teamRepository;
    private final WorkspaceMembershipRepository workspaceMembershipRepository;
    private final WorkspaceTeamSettingsService workspaceTeamSettingsService;

    public WorkspaceTeamLabelService(
        WorkspaceRepository workspaceRepository,
        TeamRepository teamRepository,
        WorkspaceMembershipRepository workspaceMembershipRepository,
        WorkspaceTeamSettingsService workspaceTeamSettingsService
    ) {
        this.workspaceRepository = workspaceRepository;
        this.teamRepository = teamRepository;
        this.workspaceMembershipRepository = workspaceMembershipRepository;
        this.workspaceTeamSettingsService = workspaceTeamSettingsService;
    }

    // ========================================================================
    // User/Team Queries
    // ========================================================================

    public List<UserTeamsDTO> getUsersWithTeams(String slug) {
        Workspace workspace = requireWorkspace(slug);
        log.debug(
            "Retrieved users with teams: workspaceId={}, workspaceSlug={}",
            workspace.getId(),
            LoggingUtils.sanitizeForLog(slug)
        );
        List<User> users = workspaceMembershipRepository.findHumanUsersWithTeamsByWorkspaceId(workspace.getId());
        Set<Long> hiddenTeamIds = workspaceTeamSettingsService.getHiddenTeamIds(workspace.getId());
        return users
            .stream()
            .map(user -> UserTeamsDTO.fromUserWithScopeSettings(user, hiddenTeamIds))
            .toList();
    }

    public List<UserTeamsDTO> getUsersWithTeams(WorkspaceContext workspaceContext) {
        return getUsersWithTeams(requireSlug(workspaceContext));
    }

    // ========================================================================
    // Label Management
    // ========================================================================

    /**
     * Adds a label as a filter for a team in a workspace.
     * Uses workspace-scoped settings for multi-workspace support.
     *
     * @param slug the workspace slug
     * @param teamId the team ID
     * @param repositoryId the repository ID the label belongs to
     * @param label the label name
     * @return the updated team info, or empty if team/label not found
     */
    public Optional<TeamInfoDTO> addLabelToTeam(String slug, Long teamId, Long repositoryId, String label) {
        Workspace workspace = requireWorkspace(slug);
        log.debug(
            "Adding label to team: labelName={}, repositoryId={}, teamId={}, workspaceId={}",
            LoggingUtils.sanitizeForLog(label),
            repositoryId,
            teamId,
            workspace.getId()
        );

        // Use workspace-scoped label filter settings
        Optional<de.tum.in.www1.hephaestus.workspace.settings.WorkspaceTeamLabelFilter> filterOpt =
            workspaceTeamSettingsService.addLabelFilterByName(workspace, teamId, repositoryId, label);

        if (filterOpt.isEmpty()) {
            log.warn("Skipped label filter addition: reason=teamOrLabelNotFound, teamId={}, labelName={}", teamId, label);
            return Optional.empty();
        }

        // Return updated team info with workspace-scoped settings
        return teamRepository
            .findWithCollectionsById(teamId)
            .map(team -> createTeamInfoDTOWithWorkspaceSettings(workspace, team));
    }

    public Optional<TeamInfoDTO> addLabelToTeam(
        WorkspaceContext workspaceContext,
        Long teamId,
        Long repositoryId,
        String label
    ) {
        return addLabelToTeam(requireSlug(workspaceContext), teamId, repositoryId, label);
    }

    /**
     * Removes a label filter for a team in a workspace.
     * Uses workspace-scoped settings for multi-workspace support.
     *
     * @param slug the workspace slug
     * @param teamId the team ID
     * @param labelId the label ID
     * @return the updated team info, or empty if team not found
     */
    public Optional<TeamInfoDTO> removeLabelFromTeam(String slug, Long teamId, Long labelId) {
        Workspace workspace = requireWorkspace(slug);
        log.debug(
            "Removing label from team: labelId={}, teamId={}, workspaceId={}",
            labelId,
            teamId,
            workspace.getId()
        );

        // Use workspace-scoped label filter settings
        boolean removed = workspaceTeamSettingsService.removeLabelFilter(workspace, teamId, labelId);
        log.debug("Completed label filter removal: removed={}", removed);

        // Return updated team info with workspace-scoped settings
        return teamRepository
            .findWithCollectionsById(teamId)
            .map(team -> createTeamInfoDTOWithWorkspaceSettings(workspace, team));
    }

    public Optional<TeamInfoDTO> removeLabelFromTeam(WorkspaceContext workspaceContext, Long teamId, Long labelId) {
        return removeLabelFromTeam(requireSlug(workspaceContext), teamId, labelId);
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Creates a TeamInfoDTO with workspace-scoped settings applied.
     */
    private TeamInfoDTO createTeamInfoDTOWithWorkspaceSettings(Workspace workspace, Team team) {
        Long workspaceId = workspace.getId();
        boolean isHidden = workspaceTeamSettingsService.isTeamHidden(workspaceId, team.getId());
        Set<Label> workspaceLabels = workspaceTeamSettingsService.getTeamLabelFilters(workspaceId, team.getId());
        Set<Long> hiddenRepoIds = workspaceTeamSettingsService.getHiddenRepositoryIds(workspaceId);

        return TeamInfoDTO.fromTeamWithScopeSettings(team, isHidden, workspaceLabels, hiddenRepoIds);
    }

    private Workspace requireWorkspace(String slug) {
        if (isBlank(slug)) {
            throw new IllegalArgumentException("Workspace slug must not be blank.");
        }
        return workspaceRepository
            .findByWorkspaceSlug(slug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", slug));
    }

    private String requireSlug(WorkspaceContext workspaceContext) {
        Objects.requireNonNull(workspaceContext, "WorkspaceContext must not be null");
        String slug = workspaceContext.slug();
        if (isBlank(slug)) {
            throw new IllegalArgumentException("Workspace context slug must not be blank.");
        }
        return slug;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
