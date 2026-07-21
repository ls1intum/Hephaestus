package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.AuditExempt;
import de.tum.cit.aet.hephaestus.core.Audited;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Workspace-admin management of "your AI provider" — a workspace's own, tenant-scoped LLM connection
 * (#1368). Mutations are gated on the instance-wide {@code allow_workspace_connections} switch inside
 * {@link WorkspaceLlmConnectionService}.
 */
@WorkspaceScopedController
@RequestMapping("/llm/connections")
@Tag(name = "Workspace LLM", description = "Workspace-scoped \"bring your own\" AI provider connections")
@RequiredArgsConstructor
@Validated
public class WorkspaceLlmConnectionController {

    private final WorkspaceLlmConnectionService connectionService;

    @GetMapping
    @Operation(summary = "List your AI provider connections", operationId = "workspaceListLlmConnections")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<List<WorkspaceLlmConnectionDTO>> list(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(
            connectionService.list(workspaceContext).stream().map(WorkspaceLlmConnectionDTO::from).toList()
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one of your AI provider connections", operationId = "workspaceGetLlmConnection")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<WorkspaceLlmConnectionDTO> get(WorkspaceContext workspaceContext, @PathVariable Long id) {
        return ResponseEntity.ok(WorkspaceLlmConnectionDTO.from(connectionService.get(workspaceContext, id)));
    }

    @PostMapping
    @Operation(summary = "Connect your own AI provider", operationId = "workspaceCreateLlmConnection")
    @RequireAtLeastWorkspaceAdmin
    @Audited("WORKSPACE_LLM_CONNECTION")
    public ResponseEntity<WorkspaceLlmConnectionDTO> create(
        WorkspaceContext workspaceContext,
        @Valid @RequestBody CreateWorkspaceLlmConnectionRequestDTO request
    ) {
        WorkspaceLlmConnection created = connectionService.create(workspaceContext, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();
        return ResponseEntity.created(location).body(WorkspaceLlmConnectionDTO.from(created));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update your AI provider connection", operationId = "workspaceUpdateLlmConnection")
    @RequireAtLeastWorkspaceAdmin
    @Audited("WORKSPACE_LLM_CONNECTION")
    public ResponseEntity<WorkspaceLlmConnectionDTO> update(
        WorkspaceContext workspaceContext,
        @PathVariable Long id,
        @Valid @RequestBody UpdateWorkspaceLlmConnectionRequestDTO request
    ) {
        return ResponseEntity.ok(
            WorkspaceLlmConnectionDTO.from(connectionService.update(workspaceContext, id, request))
        );
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove your AI provider connection", operationId = "workspaceDeleteLlmConnection")
    @RequireAtLeastWorkspaceAdmin
    @Audited("WORKSPACE_LLM_CONNECTION")
    public ResponseEntity<Void> delete(WorkspaceContext workspaceContext, @PathVariable Long id) {
        connectionService.delete(workspaceContext, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/probe")
    @Operation(summary = "Test your AI provider connection", operationId = "workspaceProbeLlmConnection")
    @RequireAtLeastWorkspaceAdmin
    @AuditExempt(reason = "tests a stored credential; stores no configuration")
    public ResponseEntity<WorkspaceLlmProbeResultDTO> probe(WorkspaceContext workspaceContext, @PathVariable Long id) {
        return ResponseEntity.ok(connectionService.probe(workspaceContext, id));
    }
}
