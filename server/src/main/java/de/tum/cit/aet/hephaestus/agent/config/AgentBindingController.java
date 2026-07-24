package de.tum.cit.aet.hephaestus.agent.config;

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
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Per-purpose agent bindings for a workspace (#1368): what model, with what limits, runs practice
 * detection and the mentor. Replaces the named-config + separate-binding flow with one write per
 * purpose.
 */
@WorkspaceScopedController
@RequestMapping("/agent-bindings")
@Tag(name = "Agent Binding", description = "Workspace-scoped per-purpose model bindings")
@RequiredArgsConstructor
@Validated
public class AgentBindingController {

    private final AgentBindingService agentBindingService;

    @GetMapping
    @Operation(summary = "List the workspace's agent bindings")
    @ApiResponse(responseCode = "200", description = "Bindings returned")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<List<AgentBindingDTO>> getBindings(WorkspaceContext workspaceContext) {
        List<AgentBindingDTO> bindings = agentBindingService
            .getBindings(workspaceContext)
            .stream()
            .map(binding -> AgentBindingDTO.from(binding, agentBindingService.isReady(binding)))
            .toList();
        return ResponseEntity.ok(bindings);
    }

    @PutMapping("/{purpose}")
    @Operation(summary = "Bind a model and limits to an agent purpose")
    @ApiResponse(
        responseCode = "200",
        description = "Binding saved",
        content = @Content(schema = @Schema(implementation = AgentBindingDTO.class))
    )
    @ApiResponse(
        responseCode = "404",
        description = "Model not found",
        content = @Content(schema = @Schema(hidden = true))
    )
    @RequireAtLeastWorkspaceAdmin
    @Audited("AI_CONFIG_BINDING")
    public ResponseEntity<AgentBindingDTO> upsertBinding(
        WorkspaceContext workspaceContext,
        @PathVariable AgentPurpose purpose,
        @Valid @RequestBody UpdateAgentBindingRequestDTO request
    ) {
        WorkspaceAgentBinding binding = agentBindingService.upsertBinding(workspaceContext, purpose, request);
        return ResponseEntity.ok(AgentBindingDTO.from(binding, agentBindingService.isReady(binding)));
    }

    @DeleteMapping("/{purpose}")
    @Operation(summary = "Unbind an agent purpose (turn it off)")
    @ApiResponse(responseCode = "204", description = "Binding removed")
    @RequireAtLeastWorkspaceAdmin
    @Audited("AI_CONFIG_BINDING")
    public ResponseEntity<Void> deleteBinding(WorkspaceContext workspaceContext, @PathVariable AgentPurpose purpose) {
        agentBindingService.deleteBinding(workspaceContext, purpose);
        return ResponseEntity.noContent().build();
    }
}
