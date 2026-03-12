package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@WorkspaceScopedController
@RequestMapping("/agent-config")
@Tag(name = "Agent Config", description = "Workspace-scoped agent configuration management")
@RequiredArgsConstructor
@Validated
public class AgentConfigController {

    private final AgentConfigService agentConfigService;

    @GetMapping
    @Operation(summary = "Get agent configuration for a workspace")
    @ApiResponse(
        responseCode = "200",
        description = "Agent config returned",
        content = @Content(schema = @Schema(implementation = AgentConfigDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "No agent config exists for this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<AgentConfigDTO> getConfig(WorkspaceContext workspaceContext) {
        return agentConfigService
            .getConfig(workspaceContext)
            .map(AgentConfigDTO::from)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new EntityNotFoundException("AgentConfig", workspaceContext.slug()));
    }

    @PutMapping
    @Operation(summary = "Create or update agent configuration for a workspace")
    @ApiResponse(
        responseCode = "200",
        description = "Agent config created or updated",
        content = @Content(schema = @Schema(implementation = AgentConfigDTO.class))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<AgentConfigDTO> createOrUpdateConfig(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody UpdateAgentConfigRequestDTO request
    ) {
        AgentConfig config = agentConfigService.createOrUpdateConfig(workspaceContext, request);
        return ResponseEntity.ok(AgentConfigDTO.from(config));
    }

    @DeleteMapping
    @Operation(summary = "Delete agent configuration for a workspace")
    @ApiResponse(responseCode = "204", description = "Agent config deleted")
    @ApiResponse(
        responseCode = "404",
        description = "No agent config exists for this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Cannot delete config with active jobs",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<Void> deleteConfig(WorkspaceContext workspaceContext) {
        agentConfigService.deleteConfig(workspaceContext);
        return ResponseEntity.noContent().build();
    }
}
