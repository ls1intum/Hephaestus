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
import java.net.URI;
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
    @ApiResponse(responseCode = "200", description = "Connection health snapshot returned")
    @ApiResponse(
        responseCode = "404",
        description = "The workspace has no ACTIVE Outline connection",
        content = @Content(schema = @Schema(hidden = true))
    )
    public ResponseEntity<OutlineConnectionStatusDTO> getOutlineConnectionStatus(WorkspaceContext workspace) {
        return ResponseEntity.ok(adminService.status(workspace.id()));
    }

    @PostMapping("/sync")
    @Operation(
        operationId = "syncOutlineConnection",
        summary = "Trigger the full Outline reconcile for this workspace (async, 202)",
        description = "Runs off the request thread. Always answers 202 with the connection status resource in the " +
            "Location header; a duplicate trigger while a reconcile is still running starts nothing new and " +
            "returns the same 202 pointing at the same monitor."
    )
    @ApiResponse(
        responseCode = "202",
        description = "Reconcile accepted (or already running — duplicate submits are absorbed); poll the " +
            "connection status resource in the Location header"
    )
    @ApiResponse(
        responseCode = "404",
        description = "The workspace has no ACTIVE Outline connection",
        content = @Content(schema = @Schema(hidden = true))
    )
    public ResponseEntity<Void> syncOutlineConnection(WorkspaceContext workspace) {
        adminService.syncNow(workspace.id());
        URI statusLocation = URI.create("/workspaces/" + workspace.slug() + "/connections/outline/status");
        return ResponseEntity.accepted().location(statusLocation).build();
    }
}
