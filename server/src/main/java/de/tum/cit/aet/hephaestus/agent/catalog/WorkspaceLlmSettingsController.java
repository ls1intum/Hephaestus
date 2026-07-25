package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * What the instance's LLM policy permits in this workspace.
 *
 * <p>Mirrors {@code GET /admin/llm/settings} one scope down: same noun, same shape, no write side
 * because the policy is not this workspace's to change.
 */
@WorkspaceScopedController
@RequestMapping("/llm/settings")
@Tag(name = "Workspace LLM", description = "Workspace-scoped \"bring your own\" AI provider connections")
@RequiredArgsConstructor
@Validated
public class WorkspaceLlmSettingsController {

    private final InstanceLlmSettingsService instanceLlmSettingsService;

    @GetMapping
    @Operation(
        summary = "Get the instance LLM policy as it applies to this workspace",
        operationId = "workspaceGetLlmSettings"
    )
    @RequireAtLeastWorkspaceAdmin
    public ResponseEntity<WorkspaceLlmSettingsDTO> get() {
        return ResponseEntity.ok(
            new WorkspaceLlmSettingsDTO(instanceLlmSettingsService.get().isAllowWorkspaceConnections())
        );
    }
}
