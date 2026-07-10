package de.tum.cit.aet.hephaestus.integration.outline.connect;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Outline admin connection surface — the health snapshot and the manual full-reconcile trigger,
 * mirroring {@code SlackConnectionAdminController}'s placement under {@code /connections/<vendor>}.
 * Both endpoints resolve to 404 {@code ProblemDetail} when the workspace has no ACTIVE Outline
 * connection; everything else flows through the shared advice chain.
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

    @GetMapping("/status")
    @Operation(
        operationId = "getOutlineConnectionStatus",
        summary = "Health of the workspace's active Outline connection (webhook, last sync, document count)"
    )
    public ResponseEntity<OutlineConnectionStatusDTO> getOutlineConnectionStatus(WorkspaceContext workspace) {
        return ResponseEntity.ok(adminService.status(workspace.id()));
    }

    /**
     * Kicks the authoritative full reconcile for this workspace and returns immediately — the sync
     * runs off the request thread, so a large corpus never stalls (or times out) the admin request.
     */
    @PostMapping("/sync")
    @Operation(
        operationId = "syncOutlineConnection",
        summary = "Trigger the full Outline reconcile for this workspace (fire-and-forget, 202)"
    )
    public ResponseEntity<Void> syncOutlineConnection(WorkspaceContext workspace) {
        adminService.syncNow(workspace.id());
        return ResponseEntity.accepted().build();
    }
}
