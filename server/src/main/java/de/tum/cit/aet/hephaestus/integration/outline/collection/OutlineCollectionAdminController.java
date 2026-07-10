package de.tum.cit.aet.hephaestus.integration.outline.collection;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.outline.collection.OutlineCollectionAdminService.RegistrationOutcome;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Per-workspace Outline collection mirroring control plane — the admin surface that decides which
 * collections are mirrored ({@code register → ENABLED ⇄ PAUSED → deleted}), mirroring
 * {@link de.tum.cit.aet.hephaestus.integration.slack.channel.SlackChannelAdminController}'s
 * resource-oriented lifecycle convention (a PATCH to the target state driving a guarded, idempotent
 * transition). Outline-side sharing ≠ Hephaestus consent, so mirroring choices live in the webapp
 * admin plane guarded by {@link RequireAtLeastWorkspaceAdmin}.
 *
 * <p>The path variable is the Outline collection UUID — the stable natural key
 * {@code (workspace_id, connection_id, collection_id)}. Every method scopes on the
 * {@link WorkspaceContext} workspace id, so another workspace's collection resolves to 404
 * (isolation), and every method 404s when the workspace has no ACTIVE Outline connection. Outline
 * API failures on the live-proxy paths surface as 502/503 {@code ProblemDetail} through
 * {@link OutlineCollectionControllerAdvice}; not-found / validation / auth flow through the shared
 * advice chain.
 */
@WorkspaceScopedController
@RequestMapping("/outline/collections")
@RequireAtLeastWorkspaceAdmin
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
@Validated
@Tag(name = "Outline collections", description = "Per-workspace Outline collection mirroring control plane")
public class OutlineCollectionAdminController {

    private final OutlineCollectionAdminService adminService;

    public OutlineCollectionAdminController(OutlineCollectionAdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping
    @Operation(
        operationId = "listOutlineCollections",
        summary = "List the workspace's mirrored Outline collections with their sync state"
    )
    public ResponseEntity<List<OutlineCollectionDTO>> listOutlineCollections(WorkspaceContext workspace) {
        return ResponseEntity.ok(adminService.listCollections(workspace.id()));
    }

    @GetMapping("/candidates")
    @Operation(
        operationId = "listOutlineCollectionCandidates",
        summary = "List the Outline collections available to mirror (live proxy; doubles as connectivity probe)"
    )
    public ResponseEntity<List<OutlineCollectionCandidateDTO>> listOutlineCollectionCandidates(
        WorkspaceContext workspace
    ) {
        return ResponseEntity.ok(adminService.listCandidates(workspace.id()));
    }

    @PostMapping
    @Operation(
        operationId = "registerOutlineCollection",
        summary = "Register an Outline collection for mirroring (lands ENABLED + PENDING; idempotent on the id)"
    )
    public ResponseEntity<OutlineCollectionDTO> registerOutlineCollection(
        WorkspaceContext workspace,
        @Valid @RequestBody RegisterOutlineCollectionRequestDTO request
    ) {
        RegistrationOutcome outcome = adminService.register(workspace.id(), request.collectionId());
        return ResponseEntity.status(outcome.created() ? HttpStatus.CREATED : HttpStatus.OK).body(outcome.collection());
    }

    @PatchMapping("/{collectionId}")
    @Operation(
        operationId = "updateOutlineCollectionState",
        summary = "Transition a mirrored collection to a target mirror state (pause / resume)"
    )
    public ResponseEntity<OutlineCollectionDTO> updateOutlineCollectionState(
        WorkspaceContext workspace,
        @PathVariable String collectionId,
        @Valid @RequestBody UpdateOutlineCollectionStateRequestDTO request
    ) {
        return ResponseEntity.ok(adminService.updateState(workspace.id(), collectionId, request.state()));
    }

    @DeleteMapping("/{collectionId}")
    @Operation(
        operationId = "deleteOutlineCollection",
        summary = "Remove a collection from the mirror and erase its mirrored documents (terminal)"
    )
    public ResponseEntity<Void> deleteOutlineCollection(WorkspaceContext workspace, @PathVariable String collectionId) {
        adminService.delete(workspace.id(), collectionId);
        return ResponseEntity.noContent().build();
    }
}
