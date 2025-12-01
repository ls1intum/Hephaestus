package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.gitprovider.team.TeamInfoDTO;
import de.tum.in.www1.hephaestus.gitprovider.user.UserTeamsDTO;
import de.tum.in.www1.hephaestus.workspace.dto.*;
import de.tum.in.www1.hephaestus.workspace.validation.WorkspaceSlug;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
@Validated
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceLifecycleService workspaceLifecycleService;

    @PostMapping
    @Operation(summary = "Create a new workspace")
    @ApiResponse(
        responseCode = "201",
        description = "Workspace created",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    public ResponseEntity<WorkspaceDTO> createWorkspace(
        @Valid @RequestBody CreateWorkspaceRequestDTO createWorkspaceRequest
    ) {
        Workspace workspace = workspaceService.createWorkspace(
            createWorkspaceRequest.workspaceSlug(),
            createWorkspaceRequest.displayName(),
            createWorkspaceRequest.accountLogin(),
            createWorkspaceRequest.accountType(),
            createWorkspaceRequest.ownerUserId()
        );

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{workspaceSlug}")
            .buildAndExpand(workspace.getWorkspaceSlug())
            .toUri();

        return ResponseEntity.created(location).body(WorkspaceDTO.from(workspace));
    }

    @GetMapping
    @Operation(summary = "List all workspaces")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = WorkspaceListItemDTO.class)))
    )
    public ResponseEntity<List<WorkspaceListItemDTO>> listWorkspaces() {
        List<WorkspaceListItemDTO> workspaces = workspaceService
            .listAllWorkspaces()
            .stream()
            .map(WorkspaceListItemDTO::from)
            .toList();
        return ResponseEntity.ok(workspaces);
    }

    @GetMapping("/{workspaceSlug}")
    @Operation(summary = "Fetch a workspace by slug")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace returned",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    public ResponseEntity<WorkspaceDTO> getWorkspace(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug
    ) {
        Workspace workspace = workspaceService
            .getWorkspaceBySlug(workspaceSlug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceSlug));
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @PatchMapping("/{workspaceSlug}/status")
    @Operation(summary = "Update workspace lifecycle status")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace updated",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    public ResponseEntity<WorkspaceDTO> updateStatus(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug,
        @Valid @RequestBody UpdateWorkspaceStatusRequestDTO request
    ) {
        Workspace workspace = workspaceLifecycleService.updateStatus(workspaceSlug, request.status());
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @DeleteMapping("/{workspaceSlug}")
    @Operation(summary = "Purge (soft delete) a workspace")
    @ApiResponse(responseCode = "204", description = "Workspace purged")
    public ResponseEntity<Void> purgeWorkspace(@PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug) {
        workspaceLifecycleService.purgeWorkspace(workspaceSlug);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{workspaceSlug}/schedule")
    @Operation(summary = "Update leaderboard schedule configuration")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace updated",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    public ResponseEntity<WorkspaceDTO> updateSchedule(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug,
        @Valid @RequestBody UpdateWorkspaceScheduleRequestDTO request
    ) {
        Workspace workspace = workspaceService.updateSchedule(workspaceSlug, request.day(), request.time());
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @PatchMapping("/{workspaceSlug}/notifications")
    @Operation(summary = "Update leaderboard notification preferences")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace updated",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    public ResponseEntity<WorkspaceDTO> updateNotifications(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug,
        @Valid @RequestBody UpdateWorkspaceNotificationsRequestDTO request
    ) {
        Workspace workspace = workspaceService.updateNotifications(
            workspaceSlug,
            request.enabled(),
            request.team(),
            request.channelId()
        );
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @PatchMapping("/{workspaceSlug}/token")
    @Operation(summary = "Update workspace Personal Access Token")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace updated",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    public ResponseEntity<WorkspaceDTO> updateToken(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug,
        @Valid @RequestBody UpdateWorkspaceTokenRequestDTO request
    ) {
        Workspace workspace = workspaceService.updateToken(workspaceSlug, request.personalAccessToken());
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @PatchMapping("/{workspaceSlug}/slack-credentials")
    @Operation(summary = "Update Slack credentials for a workspace")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace updated",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    public ResponseEntity<WorkspaceDTO> updateSlackCredentials(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug,
        @Valid @RequestBody UpdateWorkspaceSlackCredentialsRequestDTO request
    ) {
        Workspace workspace = workspaceService.updateSlackCredentials(
            workspaceSlug,
            request.slackToken(),
            request.slackSigningSecret()
        );
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @PatchMapping("/{workspaceSlug}/public-visibility")
    @Operation(summary = "Toggle public visibility for a workspace")
    @ApiResponse(
        responseCode = "200",
        description = "Workspace updated",
        content = @Content(schema = @Schema(implementation = WorkspaceDTO.class))
    )
    public ResponseEntity<WorkspaceDTO> updatePublicVisibility(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug,
        @Valid @RequestBody UpdateWorkspacePublicVisibilityRequestDTO request
    ) {
        Workspace workspace = workspaceService.updatePublicVisibility(workspaceSlug, request.isPubliclyViewable());
        return ResponseEntity.ok(WorkspaceDTO.from(workspace));
    }

    @GetMapping("/{workspaceSlug}/repositories")
    @Operation(summary = "List repositories monitored by a workspace")
    @ApiResponse(
        responseCode = "200",
        description = "Repository list",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))
    )
    public ResponseEntity<List<String>> getRepositoriesToMonitor(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug
    ) {
        var repositories = workspaceService.getRepositoriesToMonitor(workspaceSlug).stream().sorted().toList();
        return ResponseEntity.ok(repositories);
    }

    @PostMapping("/{workspaceSlug}/repositories/{owner}/{name}")
    @Operation(summary = "Add a repository to a workspace monitor list")
    public ResponseEntity<Void> addRepositoryToMonitor(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug,
        @PathVariable String owner,
        @PathVariable String name
    ) {
        workspaceService.addRepositoryToMonitor(workspaceSlug, owner + '/' + name);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
        return ResponseEntity.created(location).build();
    }

    @DeleteMapping("/{workspaceSlug}/repositories/{owner}/{name}")
    @Operation(summary = "Remove a repository from a workspace monitor list")
    public ResponseEntity<Void> removeRepositoryToMonitor(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug,
        @PathVariable String owner,
        @PathVariable String name
    ) {
        workspaceService.removeRepositoryToMonitor(workspaceSlug, owner + '/' + name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{workspaceSlug}/users")
    @Operation(summary = "List workspace users and the teams they belong to")
    public ResponseEntity<List<UserTeamsDTO>> getUsersWithTeams(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug
    ) {
        return ResponseEntity.ok(workspaceService.getUsersWithTeams(workspaceSlug));
    }

    @PostMapping("/{workspaceSlug}/teams/{teamId}/labels/{repositoryId}/{label}")
    @Operation(summary = "Add a repository label to a team")
    public ResponseEntity<TeamInfoDTO> addLabelToTeam(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug,
        @PathVariable Long teamId,
        @PathVariable Long repositoryId,
        @PathVariable String label
    ) {
        return workspaceService
            .addLabelToTeam(workspaceSlug, teamId, repositoryId, label)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{workspaceSlug}/teams/{teamId}/labels/{labelId}")
    @Operation(summary = "Remove a repository label from a team")
    public ResponseEntity<TeamInfoDTO> removeLabelFromTeam(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug,
        @PathVariable Long teamId,
        @PathVariable Long labelId
    ) {
        return workspaceService
            .removeLabelFromTeam(workspaceSlug, teamId, labelId)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{workspaceSlug}/league/reset")
    @Operation(summary = "Reset and recalculate workspace leagues")
    public ResponseEntity<Void> resetAndRecalculateLeagues(
        @PathVariable("workspaceSlug") @WorkspaceSlug String workspaceSlug
    ) {
        workspaceService.resetAndRecalculateLeagues(workspaceSlug);
        return ResponseEntity.ok().build();
    }
}
