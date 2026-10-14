package de.tum.cit.aet.hephaestus.integration.scm.workspace.settings;

import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.LabelInfoDTO;
import de.tum.cit.aet.hephaestus.integration.scm.workspace.settings.dto.UpdateRepositorySettingsRequestDTO;
import de.tum.cit.aet.hephaestus.integration.scm.workspace.settings.dto.UpdateTeamSettingsRequestDTO;
import de.tum.cit.aet.hephaestus.integration.scm.workspace.settings.dto.WorkspaceTeamRepositorySettingsDTO;
import de.tum.cit.aet.hephaestus.integration.scm.workspace.settings.dto.WorkspaceTeamSettingsDTO;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContextResolver;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@WorkspaceScopedController
@RequestMapping("/teams/{teamId}/settings")
@Tag(name = "Team Settings", description = "Workspace-scoped team settings management")
@Validated
public class WorkspaceTeamSettingsController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceTeamSettingsController.class);

    private final WorkspaceTeamSettingsService settingsService;
    private final WorkspaceContextResolver workspaceResolver;

    public WorkspaceTeamSettingsController(
            WorkspaceTeamSettingsService settingsService, WorkspaceContextResolver workspaceResolver) {
        this.settingsService = settingsService;
        this.workspaceResolver = workspaceResolver;
    }

    // Team Visibility Settings

    @GetMapping
    @Operation(
            summary = "Get team settings",
            description = "Returns the visibility settings for a team in the workspace")
    @ApiResponse(
            responseCode = "200",
            description = "Team settings returned",
            content = @Content(schema = @Schema(implementation = WorkspaceTeamSettingsDTO.class)))
    public ResponseEntity<WorkspaceTeamSettingsDTO> getTeamSettings(
            WorkspaceContext workspaceContext, @PathVariable Long teamId) {
        log.info("Getting team settings: teamId={}, workspaceSlug={}", teamId, workspaceContext.slug());

        WorkspaceTeamSettingsDTO dto = settingsService
                .getTeamSettings(workspaceContext.id(), teamId)
                .map(WorkspaceTeamSettingsDTO::from)
                .orElseGet(() -> WorkspaceTeamSettingsDTO.defaultSettings(workspaceContext.id(), teamId));

        return ResponseEntity.ok(dto);
    }

    @PatchMapping
    @RequireAtLeastWorkspaceAdmin
    @Operation(
            summary = "Update team settings",
            description = "Updates the visibility settings for a team in the workspace")
    @ApiResponse(
            responseCode = "200",
            description = "Team settings updated",
            content = @Content(schema = @Schema(implementation = WorkspaceTeamSettingsDTO.class)))
    @ApiResponse(responseCode = "404", description = "Team not found in workspace")
    @AuditExempt(reason = "team reporting scope; grants no access")
    public ResponseEntity<WorkspaceTeamSettingsDTO> updateTeamSettings(
            WorkspaceContext workspaceContext,
            @PathVariable Long teamId,
            @Valid @RequestBody UpdateTeamSettingsRequestDTO request) {
        log.info(
                "Updating team settings: teamId={}, workspaceSlug={}, hidden={}",
                teamId,
                workspaceContext.slug(),
                request.hidden());

        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);

        return settingsService
                .updateTeamVisibility(workspace, teamId, request.hidden())
                .map(WorkspaceTeamSettingsDTO::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Repository Contribution Visibility Settings

    @GetMapping("/repositories/{repositoryId}")
    @Operation(
            summary = "Get repository settings",
            description = "Returns the contribution visibility settings for a repository in a team")
    @ApiResponse(
            responseCode = "200",
            description = "Repository settings returned",
            content = @Content(schema = @Schema(implementation = WorkspaceTeamRepositorySettingsDTO.class)))
    public ResponseEntity<WorkspaceTeamRepositorySettingsDTO> getRepositorySettings(
            WorkspaceContext workspaceContext, @PathVariable Long teamId, @PathVariable Long repositoryId) {
        log.info(
                "Getting repository settings: repositoryId={}, teamId={}, workspaceSlug={}",
                repositoryId,
                teamId,
                workspaceContext.slug());

        WorkspaceTeamRepositorySettingsDTO dto = settingsService
                .getRepositorySettings(workspaceContext.id(), teamId, repositoryId)
                .map(WorkspaceTeamRepositorySettingsDTO::from)
                .orElseGet(() -> WorkspaceTeamRepositorySettingsDTO.defaultSettings(
                        workspaceContext.id(), teamId, repositoryId));

        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/repositories/{repositoryId}")
    @RequireAtLeastWorkspaceAdmin
    @Operation(
            summary = "Update repository settings",
            description = "Updates the contribution visibility settings for a repository in a team")
    @ApiResponse(
            responseCode = "200",
            description = "Repository settings updated",
            content = @Content(schema = @Schema(implementation = WorkspaceTeamRepositorySettingsDTO.class)))
    @ApiResponse(responseCode = "404", description = "Team or repository not found")
    @AuditExempt(reason = "team reporting scope; grants no access")
    public ResponseEntity<WorkspaceTeamRepositorySettingsDTO> updateRepositorySettings(
            WorkspaceContext workspaceContext,
            @PathVariable Long teamId,
            @PathVariable Long repositoryId,
            @Valid @RequestBody UpdateRepositorySettingsRequestDTO request) {
        log.info(
                "Updating repository settings: repositoryId={}, teamId={}, workspaceSlug={},"
                        + " hiddenFromContributions={}",
                repositoryId,
                teamId,
                workspaceContext.slug(),
                request.hiddenFromContributions());

        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);

        return settingsService
                .updateRepositoryVisibility(workspace, teamId, repositoryId, request.hiddenFromContributions())
                .map(WorkspaceTeamRepositorySettingsDTO::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Label Filter Settings

    @GetMapping("/label-filters")
    @Operation(
            summary = "Get team label filters",
            description = "Returns all labels configured as filters for a team in the workspace")
    @ApiResponse(
            responseCode = "200",
            description = "Label filters returned",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = LabelInfoDTO.class))))
    public ResponseEntity<List<LabelInfoDTO>> getLabelFilters(
            WorkspaceContext workspaceContext, @PathVariable Long teamId) {
        log.info("Getting label filters: teamId={}, workspaceSlug={}", teamId, workspaceContext.slug());

        List<LabelInfoDTO> labels = settingsService.getTeamLabelFilters(workspaceContext.id(), teamId).stream()
                .map(LabelInfoDTO::fromLabel)
                .toList();

        return ResponseEntity.ok(labels);
    }

    @PostMapping("/label-filters/{labelId}")
    @RequireAtLeastWorkspaceAdmin
    @Operation(summary = "Add label filter", description = "Adds a label as a filter for a team in the workspace")
    @ApiResponse(responseCode = "201", description = "Label filter added")
    @ApiResponse(responseCode = "404", description = "Team or label not found")
    @AuditExempt(reason = "team label filter is a reporting view, not configuration that gates delivery")
    public ResponseEntity<Void> addLabelFilter(
            WorkspaceContext workspaceContext, @PathVariable Long teamId, @PathVariable Long labelId) {
        log.info(
                "Adding label filter: labelId={}, teamId={}, workspaceSlug={}",
                labelId,
                teamId,
                workspaceContext.slug());

        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);

        return settingsService
                .addLabelFilter(workspace, teamId, labelId)
                .map(filter -> ResponseEntity.status(201).<Void>build())
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/label-filters/{labelId}")
    @RequireAtLeastWorkspaceAdmin
    @Operation(summary = "Remove label filter", description = "Removes a label filter from a team in the workspace")
    @ApiResponse(responseCode = "204", description = "Label filter removed")
    @ApiResponse(responseCode = "404", description = "Label filter not found")
    @AuditExempt(reason = "team label filter is a reporting view, not configuration that gates delivery")
    public ResponseEntity<Void> removeLabelFilter(
            WorkspaceContext workspaceContext, @PathVariable Long teamId, @PathVariable Long labelId) {
        log.info(
                "Removing label filter: labelId={}, teamId={}, workspaceSlug={}",
                labelId,
                teamId,
                workspaceContext.slug());

        Workspace workspace = workspaceResolver.requireWorkspace(workspaceContext);

        boolean removed = settingsService.removeLabelFilter(workspace, teamId, labelId);
        return removed
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
