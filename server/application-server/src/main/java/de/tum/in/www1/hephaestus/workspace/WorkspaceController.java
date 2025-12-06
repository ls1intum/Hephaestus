package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserTeamsDTO;
import de.tum.in.www1.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.in.www1.hephaestus.workspace.authorization.RequireWorkspaceOwner;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import de.tum.in.www1.hephaestus.workspace.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@WorkspaceScopedController
@RequiredArgsConstructor
@Validated
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceLifecycleService workspaceLifecycleService;

    @GetMapping
    @Operation(summary = "Fetch a workspace by slug")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace returned",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    public ResponseEntity<WorkspaceDTO> getWorkspace(WorkspaceContext workspaceContext) {
        Workspace workspace = workspaceService
            .getWorkspaceBySlug(workspaceContext.slug())
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @PatchMapping("/status")
    @Operation(summary = "Update workspace lifecycle status")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace updated",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<WorkspaceDTO> updateStatus(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody UpdateWorkspaceStatusRequestDTO request
    ) {
        Workspace workspace = workspaceLifecycleService.updateStatus(workspaceContext, request.status());
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @DeleteMapping
    @Operation(summary = "Purge (soft delete) a workspace")
    @ApiResponse(responseCode = "204", description = "Workspace purged")
    @RequireWorkspaceOwner
    public ResponseEntity<Void> purgeWorkspace(WorkspaceContext workspaceContext) {
        workspaceLifecycleService.purgeWorkspace(workspaceContext);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/schedule")
    @Operation(summary = "Update leaderboard schedule configuration")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace updated",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<WorkspaceDTO> updateSchedule(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody UpdateWorkspaceScheduleRequestDTO request
    ) {
        Workspace workspace = workspaceService.updateSchedule(workspaceContext, request.day(), request.time());
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @PatchMapping("/notifications")
    @Operation(summary = "Update leaderboard notification preferences")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace updated",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<WorkspaceDTO> updateNotifications(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody UpdateWorkspaceNotificationsRequestDTO request
    ) {
        Workspace workspace = workspaceService.updateNotifications(
            workspaceContext,
            request.enabled(),
            request.team(),
            request.channelId()
        );
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @PatchMapping("/token")
    @Operation(summary = "Update workspace Personal Access Token")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace updated",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<WorkspaceDTO> updateToken(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody UpdateWorkspaceTokenRequestDTO request
    ) {
        Workspace workspace = workspaceService.updateToken(workspaceContext, request.personalAccessToken());
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @PatchMapping("/slack-credentials")
    @Operation(summary = "Update Slack credentials for a workspace")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace updated",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<WorkspaceDTO> updateSlackCredentials(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody UpdateWorkspaceSlackCredentialsRequestDTO request
    ) {
        Workspace workspace = workspaceService.updateSlackCredentials(
            workspaceContext,
            request.slackToken(),
            request.slackSigningSecret()
        );
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @PatchMapping("/public-visibility")
    @Operation(summary = "Toggle public visibility for a workspace")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace updated",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<WorkspaceDTO> updatePublicVisibility(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody UpdateWorkspacePublicVisibilityRequestDTO request
    ) {
        Workspace workspace = workspaceService.updatePublicVisibility(workspaceContext, request.isPubliclyViewable());
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @PatchMapping("/slug")
    @Operation(summary = "Rename workspace slug and create redirect")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace renamed",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    @RequireWorkspaceOwner
    public ResponseEntity<WorkspaceDTO> renameSlug(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody RenameWorkspaceSlugRequestDTO request
    ) {
        Workspace workspace = workspaceService.renameSlug(workspaceContext, request.newSlug());
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @GetMapping("/repositories")
    @Operation(summary = "List repositories monitored by a workspace")
    @ApiResponse(
        responseCode = "200",
        description = "Repository list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))
    )
    public ResponseEntity<List<String>> getRepositoriesToMonitor(WorkspaceContext workspaceContext) {
        var repositories = workspaceService.getRepositoriesToMonitor(workspaceContext).stream().sorted().toList();
        return ResponseEntity.ok(repositories);
    }

    @PostMapping("/repositories/{owner}/{name}")
    @Operation(summary = "Add a repository to a workspace monitor list")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<Void> addRepositoryToMonitor(
        WorkspaceContext workspaceContext,
        @PathVariable String owner,
        @PathVariable String name
    ) {
        workspaceService.addRepositoryToMonitor(workspaceContext, owner + '/' + name);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.created(location).build();
    }

    @DeleteMapping("/repositories/{owner}/{name}")
    @Operation(summary = "Remove a repository from a workspace monitor list")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<Void> removeRepositoryToMonitor(
        WorkspaceContext workspaceContext,
        @PathVariable String owner,
        @PathVariable String name
    ) {
        workspaceService.removeRepositoryToMonitor(workspaceContext, owner + '/' + name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users")
    @Operation(summary = "List workspace users and the teams they belong to")
    public ResponseEntity<List<UserTeamsDTO>> getUsersWithTeams(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(workspaceService.getUsersWithTeams(workspaceContext));
    }

    @PostMapping("/teams/{teamId}/labels/{repositoryId}/{label}")
    @Operation(summary = "Add a repository label to a team")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<TeamInfoDTO> addLabelToTeam(
        WorkspaceContext workspaceContext,
        @PathVariable Long teamId,
        @PathVariable Long repositoryId,
        @PathVariable String label
    ) {
        return workspaceService
            .addLabelToTeam(workspaceContext, teamId, repositoryId, label)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/teams/{teamId}/labels/{labelId}")
    @Operation(summary = "Remove a repository label from a team")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<TeamInfoDTO> removeLabelFromTeam(
        WorkspaceContext workspaceContext,
        @PathVariable Long teamId,
        @PathVariable Long labelId
    ) {
        return workspaceService
            .removeLabelFromTeam(workspaceContext, teamId, labelId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/league/reset")
    @Operation(summary = "Reset and recalculate workspace leagues")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<Void> resetAndRecalculateLeagues(WorkspaceContext workspaceContext) {
        workspaceService.resetAndRecalculateLeagues(workspaceContext);
        return ResponseEntity.ok().build();
    }
}
