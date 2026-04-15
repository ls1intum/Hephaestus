package de.tum.in.www1.hephaestus.agent.runner;

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
@RequestMapping("/agent-runners")
@Tag(name = "Agent Runners", description = "Workspace-scoped runner management")
@Validated
public class AgentRunnerController {

    private final AgentRunnerService agentRunnerService;

    public AgentRunnerController(AgentRunnerService agentRunnerService) {
        this.agentRunnerService = agentRunnerService;
    }

    @GetMapping
    @Operation(summary = "List runners for a workspace")
    @ApiResponse(responseCode = "200", description = "Runners returned")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<List<AgentRunnerDTO>> getRunners(WorkspaceContext workspaceContext) {
        List<AgentRunnerDTO> runners = agentRunnerService
            .getRunners(workspaceContext)
            .stream()
            .map(AgentRunnerDTO::from)
            .toList();
        return ResponseEntity.ok(runners);
    }

    @GetMapping("/{runnerId}")
    @Operation(summary = "Get a specific runner")
    @ApiResponse(
        responseCode = "200",
        description = "Runner returned",
        content = @Content(schema = @Schema(implementation = AgentRunnerDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Runner not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<AgentRunnerDTO> getRunner(WorkspaceContext workspaceContext, @PathVariable Long runnerId) {
        return ResponseEntity.ok(AgentRunnerDTO.from(agentRunnerService.getRunner(workspaceContext, runnerId)));
    }

    @PostMapping
    @Operation(summary = "Create a new runner")
    @ApiResponse(
        responseCode = "201",
        description = "Runner created",
        content = @Content(schema = @Schema(implementation = AgentRunnerDTO.class))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Runner name already exists in this workspace",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<AgentRunnerDTO> createRunner(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody CreateAgentRunnerRequestDTO request
    ) {
        AgentRunner runner = agentRunnerService.createRunner(workspaceContext, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(runner.getId())
            .toUri();
        return ResponseEntity.created(location).body(AgentRunnerDTO.from(runner));
    }

    @PatchMapping("/{runnerId}")
    @Operation(summary = "Update an existing runner")
    @ApiResponse(
        responseCode = "200",
        description = "Runner updated",
        content = @Content(schema = @Schema(implementation = AgentRunnerDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Runner not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<AgentRunnerDTO> updateRunner(
        WorkspaceContext workspaceContext,
        @PathVariable Long runnerId,
        @Valid @RequestBody UpdateAgentRunnerRequestDTO request
    ) {
        return ResponseEntity.ok(
            AgentRunnerDTO.from(agentRunnerService.updateRunner(workspaceContext, runnerId, request))
        );
    }

    @DeleteMapping("/{runnerId}")
    @Operation(summary = "Delete a runner")
    @ApiResponse(responseCode = "204", description = "Runner deleted")
    @ApiResponse(
        responseCode = "404",
        description = "Runner not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @ApiResponse(
        responseCode = "409",
        description = "Cannot delete runner with linked configs",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<Void> deleteRunner(WorkspaceContext workspaceContext, @PathVariable Long runnerId) {
        agentRunnerService.deleteRunner(workspaceContext, runnerId);
        return ResponseEntity.noContent().build();
    }
}
