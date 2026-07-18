package de.tum.cit.aet.hephaestus.integration.outline.connect;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Outline admin connection surface for the kind-specific credential concern (the token health probe).
 * The health snapshot ({@code GET /connections/{id}/sync}) and the manual full-reconcile trigger
 * ({@code POST /connections/{id}/sync/jobs}) live in the unified sync-observability API — see
 * {@code de.tum.cit.aet.hephaestus.integration.outline.status.OutlineConnectionSyncStateProvider} and
 * {@code OutlineIntegrationSyncRunner}.
 */
@WorkspaceScopedController
@RequestMapping("/connections/outline")
@RequireAtLeastWorkspaceAdmin
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
@Tag(name = "Connections", description = "Workspace integration connection management")
public class OutlineConnectionAdminController {

    private final OutlineConnectionAdminService adminService;

    public OutlineConnectionAdminController(OutlineConnectionAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/token")
    @Operation(
        operationId = "getOutlineTokenStatus",
        summary = "Live state of the API token behind this workspace's Outline connection",
        description = "Probes Outline directly. Reports whether the token is still accepted and, when the token " +
            "may list its own key, its name, expiry and last use. Outline cannot rotate a key from a key, so an " +
            "expiring token is renewed in Outline and re-entered here."
    )
    @ApiResponse(responseCode = "200", description = "Token state returned")
    @ApiResponse(
        responseCode = "404",
        description = "The workspace has no ACTIVE Outline connection",
        content = @Content(schema = @Schema(hidden = true))
    )
    public ResponseEntity<OutlineTokenStatusDTO> getOutlineTokenStatus(WorkspaceContext workspace) {
        return ResponseEntity.ok(adminService.tokenStatus(workspace.id()));
    }
}
