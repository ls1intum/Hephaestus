package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the instance's LLM policy permits in this workspace; read-only, the policy is instance-wide.
 *
 * <p>Under the workspace path so {@code WorkspaceContextFilter} resolves the tenant and the caller's roles
 * before {@link RequireAtLeastWorkspaceAdmin} runs, but spelled out rather than taken from
 * {@code @WorkspaceScopedController}: the answer does not depend on which workspace asked.
 */
@RestController
@RequestMapping("/workspaces/{workspaceSlug}/llm/settings")
@Tag(
    name = "Workspace LLM",
    description = "Workspace-scoped \"bring your own\" AI provider connections, models and settings"
)
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
