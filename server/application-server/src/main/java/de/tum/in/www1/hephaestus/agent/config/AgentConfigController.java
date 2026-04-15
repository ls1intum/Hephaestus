package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@WorkspaceScopedController
@RequestMapping("/agent-configs")
@Tag(name = "Agent Config", description = "Workspace-scoped agent configuration management")
@Validated
public class AgentConfigController {

    private final AgentConfigService agentConfigService;

    public AgentConfigController(AgentConfigService agentConfigService) {
        this.agentConfigService = agentConfigService;
    }

    @GetMapping
    @Operation(summary = "List agent configurations for a workspace")
    @ApiResponse(responseCode = "200", description = "Agent configs returned")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<List<AgentConfigDTO>> getConfigs(WorkspaceContext workspaceContext) {
        List<AgentConfigDTO> configs = agentConfigService
            .getConfigs(workspaceContext)
            .stream()
            .map(AgentConfigDTO::from)
            .toList();
        return ResponseEntity.ok(configs);
    }

    @GetMapping("/{configId}")
    @Operation(summary = "Get a specific agent configuration")
    @ApiResponse(
        responseCode = "200",
        description = "Agent config returned",
        content = @Content(schema = @Schema(implementation = AgentConfigDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Agent config not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<AgentConfigDTO> getConfig(WorkspaceContext workspaceContext, @PathVariable Long configId) {
        AgentConfig config = agentConfigService.getConfig(workspaceContext, configId);
        return ResponseEntity.ok(AgentConfigDTO.from(config));
    }

    @PostMapping
    @Operation(summary = "Create a new agent configuration")
    @ApiResponse(
        responseCode = "201",
        description = "Agent config created",
        content = @Content(schema = @Schema(implementation = AgentConfigDTO.class))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Config name already exists in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<AgentConfigDTO> createConfig(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody CreateAgentConfigRequestDTO request
    ) {
        AgentConfig config = agentConfigService.createConfig(workspaceContext, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(config.getId())
            .toUri();
        return ResponseEntity.created(location).body(AgentConfigDTO.from(config));
    }

    @PatchMapping("/{configId}")
    @Operation(summary = "Update an existing agent configuration")
    @ApiResponse(
        responseCode = "200",
        description = "Agent config updated",
        content = @Content(schema = @Schema(implementation = AgentConfigDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Agent config not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<AgentConfigDTO> updateConfig(
        WorkspaceContext workspaceContext,
        @PathVariable Long configId,
        @Valid @RequestBody UpdateAgentConfigRequestDTO request
    ) {
        AgentConfig config = agentConfigService.updateConfig(workspaceContext, configId, request);
        return ResponseEntity.ok(AgentConfigDTO.from(config));
    }

    @DeleteMapping("/{configId}")
    @Operation(summary = "Delete an agent configuration")
    @ApiResponse(responseCode = "204", description = "Agent config deleted")
    @ApiResponse(
        responseCode = "404",
        description = "Agent config not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Cannot delete config with active jobs",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<Void> deleteConfig(WorkspaceContext workspaceContext, @PathVariable Long configId) {
        agentConfigService.deleteConfig(workspaceContext, configId);
        return ResponseEntity.noContent().build();
    }
}
