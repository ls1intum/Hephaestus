package de.tum.cit.aet.hephaestus.agent.settings;

import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Workspace AI-settings admin API: runtime bindings (which config powers practice detection / the
 * mentor) and practice-review policy. Granular writes; one aggregate read. Agent-config CRUD and
 * job activity live on their own controllers ({@code /agent-configs}, {@code /agent-jobs}).
 */
@WorkspaceScopedController
@RequestMapping("/ai-settings")
@Tag(
    name = "AI Settings",
    description = "Workspace-scoped AI configuration (runtime bindings + practice-review policy)"
)
@RequiredArgsConstructor
@Validated
public class AiSettingsController {

    private final AiSettingsService aiSettingsService;

    @GetMapping
    @Operation(summary = "Get aggregate workspace AI settings")
    @ApiResponse(
        responseCode = "200",
        description = "AI settings returned",
        content = @Content(schema = @Schema(implementation = AiSettingsViewDTO.class))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<AiSettingsViewDTO> getAiSettings(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(aiSettingsService.getSettings(workspaceContext));
    }

    @PutMapping("/practice-config")
    @Operation(summary = "Bind (or unbind) the agent config that powers practice detection")
    @ApiResponse(
        responseCode = "200",
        description = "Binding updated",
        content = @Content(schema = @Schema(implementation = AiSettingsViewDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Config not found in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited("AI_CONFIG_BINDING")
    public ResponseEntity<AiSettingsViewDTO> updatePracticeConfig(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody UpdateAgentBindingRequestDTO request
    ) {
        return ResponseEntity.ok(aiSettingsService.bindPracticeConfig(workspaceContext, request.configId()));
    }

    @PutMapping("/mentor-config")
    @Operation(summary = "Bind (or unbind) the agent config that powers the mentor")
    @ApiResponse(
        responseCode = "200",
        description = "Binding updated",
        content = @Content(schema = @Schema(implementation = AiSettingsViewDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Config not found in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Config is disabled or its catalog model is unavailable",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited("AI_CONFIG_BINDING")
    public ResponseEntity<AiSettingsViewDTO> updateMentorConfig(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody UpdateAgentBindingRequestDTO request
    ) {
        return ResponseEntity.ok(aiSettingsService.bindMentorConfig(workspaceContext, request.configId()));
    }

    @PatchMapping("/practice-review")
    @Operation(summary = "Update per-workspace practice-review policy")
    @ApiResponse(
        responseCode = "200",
        description = "Policy updated",
        content = @Content(schema = @Schema(implementation = AiSettingsViewDTO.class))
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited("PRACTICE_REVIEW_SETTINGS")
    public ResponseEntity<AiSettingsViewDTO> updatePracticeReviewSettings(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody UpdatePracticeReviewSettingsDTO request
    ) {
        return ResponseEntity.ok(aiSettingsService.updatePracticeReview(workspaceContext, request));
    }
}
