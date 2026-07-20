package de.tum.cit.aet.hephaestus.agent.catalog;

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
 * Workspace-admin management of models on "your AI provider" (#1368), plus the available-models
 * projection every workspace admin uses to bind a Task's model. Sibling to
 * {@link WorkspaceLlmConnectionController}; both share the {@code /llm} prefix.
 */
@WorkspaceScopedController
@RequestMapping("/llm")
@Tag(name = "Workspace LLM", description = "Workspace-scoped \"bring your own\" AI provider models")
@RequiredArgsConstructor
@Validated
public class WorkspaceLlmModelController {

    private final WorkspaceLlmModelService modelService;

    @PostMapping("/connections/{connectionId}/models")
    @Operation(summary = "Create a model on your AI provider", operationId = "workspaceCreateLlmModel")
    @RequireAtLeastWorkspaceAdmin
    @Audited("WORKSPACE_LLM_MODEL")
    public ResponseEntity<WorkspaceLlmModelDTO> create(
        WorkspaceContext workspaceContext,
        @PathVariable Long connectionId,
        @Valid @RequestBody CreateWorkspaceLlmModelRequest request
    ) {
        WorkspaceLlmModel created = modelService.create(workspaceContext, connectionId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/workspaces/{workspaceSlug}/llm/models/{id}")
            .buildAndExpand(workspaceContext.slug(), created.getId())
            .toUri();
        return ResponseEntity.created(location).body(WorkspaceLlmModelDTO.from(created));
    }

    @GetMapping("/models")
    @Operation(summary = "List models on your AI provider", operationId = "workspaceListLlmModels")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<List<WorkspaceLlmModelDTO>> list(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(modelService.list(workspaceContext).stream().map(WorkspaceLlmModelDTO::from).toList());
    }

    @GetMapping("/models/{id}")
    @Operation(summary = "Get a model on your AI provider", operationId = "workspaceGetLlmModel")
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<WorkspaceLlmModelDTO> get(WorkspaceContext workspaceContext, @PathVariable Long id) {
        return ResponseEntity.ok(WorkspaceLlmModelDTO.from(modelService.get(workspaceContext, id)));
    }

    @PatchMapping("/models/{id}")
    @Operation(summary = "Update a model on your AI provider", operationId = "workspaceUpdateLlmModel")
    @RequireAtLeastWorkspaceAdmin
    @Audited("WORKSPACE_LLM_MODEL")
    public ResponseEntity<WorkspaceLlmModelDTO> update(
        WorkspaceContext workspaceContext,
        @PathVariable Long id,
        @Valid @RequestBody UpdateWorkspaceLlmModelRequest request
    ) {
        return ResponseEntity.ok(WorkspaceLlmModelDTO.from(modelService.update(workspaceContext, id, request)));
    }

    @DeleteMapping("/models/{id}")
    @Operation(summary = "Remove a model on your AI provider", operationId = "workspaceDeleteLlmModel")
    @RequireAtLeastWorkspaceAdmin
    @Audited("WORKSPACE_LLM_MODEL")
    public ResponseEntity<Void> delete(WorkspaceContext workspaceContext, @PathVariable Long id) {
        modelService.delete(workspaceContext, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/available-models")
    @Operation(
        summary = "List models this workspace can bind a Task to (shared + your own)",
        operationId = "workspaceListAvailableLlmModels"
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<List<AvailableLlmModelDTO>> availableModels(WorkspaceContext workspaceContext) {
        return ResponseEntity.ok(modelService.availableModels(workspaceContext));
    }
}
