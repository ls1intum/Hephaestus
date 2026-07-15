package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncJobType;
import de.tum.cit.aet.hephaestus.integration.core.sync.SyncStateConflictException;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Unified sync-observability + control surface — one status/resources/jobs/trigger/cancel API for
 * every connected integration, plus the workspace's integration catalog. Thin HTTP adapter; all
 * business logic (provider/runner dispatch, health derivation, async hand-off) lives in
 * {@link SyncStatusService}.
 */
@ConditionalOnServerRole
@WorkspaceScopedController
@RequestMapping("/connections")
@RequireAtLeastWorkspaceAdmin
@Tag(name = "Sync", description = "Integration sync observability and manual controls")
@Validated
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SyncStatusService syncStatusService;

    public SyncController(SyncStatusService syncStatusService) {
        this.syncStatusService = syncStatusService;
    }

    @GetMapping("/{connectionId}/sync")
    @Operation(summary = "Unified sync status for one connection")
    public ResponseEntity<ConnectionSyncStatusDTO> getConnectionSyncStatus(
        WorkspaceContext workspace,
        @PathVariable Long connectionId
    ) {
        return ResponseEntity.ok(syncStatusService.getStatus(workspace.id(), connectionId));
    }

    @GetMapping("/{connectionId}/sync/resources")
    @Operation(summary = "Per-resource sync state (repos / channels / collections) for one connection")
    public ResponseEntity<List<SyncResourceStateDTO>> listConnectionSyncResources(
        WorkspaceContext workspace,
        @PathVariable Long connectionId
    ) {
        return ResponseEntity.ok(syncStatusService.getResources(workspace.id(), connectionId));
    }

    @GetMapping("/{connectionId}/sync/jobs")
    @Operation(summary = "Paginated sync job history for one connection")
    public ResponseEntity<Page<SyncJobDTO>> listConnectionSyncJobs(
        WorkspaceContext workspace,
        @PathVariable Long connectionId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        int safePage = Math.max(page, 0);
        int pageSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(safePage, pageSize, Sort.by("createdAt").descending());
        return ResponseEntity.ok(syncStatusService.getJobs(workspace.id(), connectionId, pageable));
    }

    /**
     * Trigger a manual sync. Idempotent-absorb: a connection that already has an active job answers
     * 200 with that job rather than erroring, so a double-click "Sync now" is harmless.
     */
    @PostMapping("/{connectionId}/sync/jobs")
    @Operation(summary = "Trigger a manual sync or backfill", operationId = "triggerSyncJob")
    @ApiResponses(
        {
            @ApiResponse(
                responseCode = "202",
                description = "A new sync job was created and dispatched",
                content = @Content(schema = @Schema(implementation = SyncJobDTO.class))
            ),
            @ApiResponse(
                responseCode = "200",
                description = "Idempotent-absorb: a same-type job was already running and is returned unchanged",
                content = @Content(schema = @Schema(implementation = SyncJobDTO.class))
            ),
            @ApiResponse(
                responseCode = "400",
                description = "Missing or invalid request body (e.g. absent sync type)",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Connection not found in this workspace",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "Connection is not ACTIVE, a different sync type is already running, or manual sync is unsupported for the kind",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
        }
    )
    public ResponseEntity<SyncJobDTO> triggerConnectionSyncJob(
        WorkspaceContext workspace,
        @PathVariable Long connectionId,
        @RequestBody @Valid @NotNull TriggerSyncJobRequestDTO body
    ) {
        Long triggeredByUserId = SecurityUtils.getCurrentAccountId().orElse(null);
        SyncStatusService.TriggerOutcome outcome = syncStatusService.triggerSync(
            workspace.id(),
            connectionId,
            body.type(),
            triggeredByUserId
        );
        return outcome.created()
            ? ResponseEntity.status(HttpStatus.ACCEPTED).body(outcome.job())
            : ResponseEntity.ok(outcome.job());
    }

    @PostMapping("/{connectionId}/sync/jobs/{jobId}/cancel")
    @Operation(summary = "Request cooperative cancellation of a running sync job")
    @ApiResponses(
        {
            @ApiResponse(
                responseCode = "202",
                description = "Cancellation requested; the running job stops cooperatively",
                content = @Content(schema = @Schema(implementation = SyncJobDTO.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "Job not found in this workspace, or not owned by this connection",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "Job is already in a terminal status",
                content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
        }
    )
    public ResponseEntity<SyncJobDTO> cancelConnectionSyncJob(
        WorkspaceContext workspace,
        @PathVariable Long connectionId,
        @PathVariable Long jobId
    ) {
        SyncJobDTO job = syncStatusService.cancelJob(workspace.id(), connectionId, jobId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/catalog")
    @Operation(summary = "Every integration kind this workspace could connect, joined against existing connections")
    public ResponseEntity<List<IntegrationCatalogEntryDTO>> getIntegrationCatalog(WorkspaceContext workspace) {
        return ResponseEntity.ok(syncStatusService.catalog(workspace.id()));
    }

    /**
     * Not-found is signalled as {@link NoSuchElementException} by {@code ConnectionAdminService}
     * (deliberately undistinguished from cross-workspace reads — see its javadoc). Handled locally
     * — rather than globally — so a stray {@code Optional.get()} elsewhere still surfaces as a 500,
     * matching {@code ConnectionController}'s precedent.
     */
    @ExceptionHandler(NoSuchElementException.class)
    ProblemDetail handleNotFound(NoSuchElementException e) {
        log.info("Sync lookup 404: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }

    /**
     * Handled locally rather than relying on {@code @ResponseStatus} or
     * {@code GlobalControllerAdvice}: {@code WorkspaceControllerAdvice} installs an unscoped,
     * {@code HIGHEST_PRECEDENCE} handler for the plain {@link IllegalStateException} family (mapping
     * to 500 for its own domain), so a distinct exception type + a local handler is the only reliable
     * way to get 409 here. See {@link SyncStateConflictException}'s javadoc.
     */
    @ExceptionHandler(SyncStateConflictException.class)
    ProblemDetail handleStateConflict(SyncStateConflictException e) {
        log.info("Sync state conflict: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Invalid state");
        // Machine-readable extension members (RFC 9457): the conflicting connection state or in-flight
        // job id/type/status, so the client can react without a follow-up refetch.
        e.properties().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(SyncNotSupportedException.class)
    ProblemDetail handleNotSupported(SyncNotSupportedException e) {
        log.info("Sync not supported: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Manual sync not supported");
        problem.setProperty("kind", e.kind());
        return problem;
    }
}
