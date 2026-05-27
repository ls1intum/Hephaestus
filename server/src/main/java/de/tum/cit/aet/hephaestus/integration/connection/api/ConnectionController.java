package de.tum.cit.aet.hephaestus.integration.connection.api;

import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService.TransitionRequest;
import de.tum.cit.aet.hephaestus.integration.spi.ConnectionStrategy;
import de.tum.cit.aet.hephaestus.integration.spi.ConnectionStrategy.ConnectInitiation;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.workspace.authorization.RequireAtLeastWorkspaceAdmin;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/**
 * Administrative REST surface for managing per-workspace
 * {@link de.tum.cit.aet.hephaestus.integration.connection.Connection} rows — list, read,
 * initiate, suspend, reactivate, disconnect, audit.
 *
 * <p>Thin HTTP adapter: all repository access lives in {@link ConnectionAdminService};
 * the state machine + audit invariants live in {@link ConnectionService}; per-kind
 * vendor flows live in {@link ConnectionStrategy} implementations. The controller's
 * only jobs are HTTP wire mapping (DTO ↔ entity), strategy resolution, and exception
 * translation.
 *
 * <p>The {@code workspaceId} path variable + class-level
 * {@link RequireAtLeastWorkspaceAdmin} satisfy the
 * {@code MultiTenancyArchitectureTest.dataEndpointsReceiveWorkspaceContext} rule — the
 * annotation simple name contains {@code Workspace}, which the rule recognises as a
 * workspace-context guard.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/connections")
@RequireAtLeastWorkspaceAdmin
public class ConnectionController {

    private static final Logger log = LoggerFactory.getLogger(ConnectionController.class);

    private static final int AUDIT_PAGE_CAP = 200;

    private final ConnectionAdminService admin;
    private final ConnectionService connectionService;
    private final ObjectMapper objectMapper;
    private final Map<IntegrationKind, ConnectionStrategy> strategies;

    public ConnectionController(
        ConnectionAdminService admin,
        ConnectionService connectionService,
        ObjectMapper objectMapper,
        List<ConnectionStrategy> strategyBeans
    ) {
        this.admin = admin;
        this.connectionService = connectionService;
        this.objectMapper = objectMapper;
        this.strategies = strategyBeans
            .stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    ConnectionStrategy::kind,
                    s -> s,
                    (a, b) -> {
                        throw new IllegalStateException(
                            "Duplicate ConnectionStrategy for kind=" +
                                a.kind() +
                                ": " +
                                a.getClass() +
                                " vs " +
                                b.getClass()
                        );
                    }
                )
            );
    }

    @GetMapping
    public ResponseEntity<List<ConnectionSummary>> list(@PathVariable Long workspaceId) {
        List<ConnectionSummary> summaries = admin
            .listForWorkspace(workspaceId)
            .stream()
            .map(c -> ConnectionSummary.from(c, admin.manifests()))
            .toList();
        return ResponseEntity.ok(summaries);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConnectionDetail> read(@PathVariable Long workspaceId, @PathVariable Long id) {
        Connection connection = admin.findInWorkspaceOrThrow(workspaceId, id);
        return ResponseEntity.ok(ConnectionDetail.from(connection, admin.manifests(), objectMapper));
    }

    @PostMapping
    public ResponseEntity<InitiateConnectionResponse> initiate(
        @PathVariable Long workspaceId,
        @RequestBody @NotNull InitiateConnectionRequest body,
        @Nullable Authentication authentication
    ) {
        if (body == null || body.kind() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "kind is required");
        }

        ConnectionStrategy strategy = strategies.get(body.kind());
        if (strategy == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "No ConnectionStrategy registered for kind=" + body.kind()
            );
        }

        Map<String, String> userInput = body.userInput() == null ? Map.of() : body.userInput();
        ConnectInitiation initiation;
        try {
            initiation = strategy.initiate(
                new ConnectionStrategy.InitiateRequest(workspaceId, body.kind(), userInput, body.redirectAfter())
            );
        } catch (IllegalArgumentException e) {
            // Strategy-level validation failure (e.g. missing 'pat' for GitLab) → 400.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }

        return switch (initiation) {
            case ConnectInitiation.RedirectToVendor r -> ResponseEntity.ok(
                new InitiateConnectionResponse.Redirect(r.vendorUrl(), r.oauthState())
            );
            case ConnectInitiation.AcceptInline inline -> {
                Connection connection = admin.createInlineConnection(
                    workspaceId,
                    body.kind(),
                    inline.instanceKey(),
                    inline.credentials(),
                    userInput,
                    actorRef(authentication)
                );
                yield ResponseEntity.ok(new InitiateConnectionResponse.Linked(connection.getId()));
            }
        };
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<ConnectionSummary> suspend(
        @PathVariable Long workspaceId,
        @PathVariable Long id,
        @RequestBody(required = false) @Nullable ReasonRequest body,
        @Nullable Authentication authentication
    ) {
        Connection connection = admin.findInWorkspaceOrThrow(workspaceId, id);
        String reason = body == null ? null : body.reason();
        connection = connectionService.transition(
            connection,
            new TransitionRequest(
                IntegrationState.SUSPENDED,
                "SUSPEND",
                "ADMIN",
                actorRef(authentication),
                "suspend-" + connection.getId() + "-" + UUID.randomUUID(),
                reason
            )
        );
        return ResponseEntity.ok(ConnectionSummary.from(connection, admin.manifests()));
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<ConnectionSummary> reactivate(
        @PathVariable Long workspaceId,
        @PathVariable Long id,
        @RequestBody(required = false) @Nullable ReasonRequest body,
        @Nullable Authentication authentication
    ) {
        Connection connection = admin.findInWorkspaceOrThrow(workspaceId, id);
        String reason = body == null ? null : body.reason();
        connection = connectionService.transition(
            connection,
            new TransitionRequest(
                IntegrationState.ACTIVE,
                "REACTIVATE",
                "ADMIN",
                actorRef(authentication),
                "reactivate-" + connection.getId() + "-" + UUID.randomUUID(),
                reason
            )
        );
        return ResponseEntity.ok(ConnectionSummary.from(connection, admin.manifests()));
    }

    @PostMapping("/{id}/disconnect")
    public ResponseEntity<Void> disconnect(
        @PathVariable Long workspaceId,
        @PathVariable Long id,
        @Nullable Authentication authentication
    ) {
        Connection connection = admin.findInWorkspaceOrThrow(workspaceId, id);

        // Best-effort vendor-side revoke. Strategy may be missing if the kind was
        // de-registered after the connection row was written; we still want the local
        // state transition to succeed so the admin can clear stale rows.
        ConnectionStrategy strategy = strategies.get(connection.getKind());
        if (strategy != null) {
            try {
                strategy.revoke(connection.toRef());
            } catch (RuntimeException e) {
                log.warn(
                    "Vendor-side revoke failed for connection={} kind={}: {} — proceeding with local UNINSTALLED transition",
                    connection.getId(),
                    connection.getKind(),
                    e.toString()
                );
            }
        } else {
            log.warn(
                "No ConnectionStrategy registered for kind={} on disconnect of connection={} — local transition only",
                connection.getKind(),
                connection.getId()
            );
        }

        connectionService.transition(
            connection,
            new TransitionRequest(
                IntegrationState.UNINSTALLED,
                "DISCONNECT",
                "ADMIN",
                actorRef(authentication),
                "disconnect-" + connection.getId() + "-" + UUID.randomUUID(),
                null
            )
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/audit")
    public ResponseEntity<List<ConnectionAuditEntry>> audit(@PathVariable Long workspaceId, @PathVariable Long id) {
        // findInWorkspaceOrThrow enforces the workspace scope before we expose audit history,
        // so cross-workspace audit reads return 404 rather than leaking a partial trail.
        admin.findInWorkspaceOrThrow(workspaceId, id);
        List<ConnectionAuditEntry> entries = admin
            .auditForConnection(id, AUDIT_PAGE_CAP)
            .stream()
            .map(ConnectionAuditEntry::from)
            .toList();
        return ResponseEntity.ok(entries);
    }

    private static String actorRef(@Nullable Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return "anonymous";
        return authentication.getName();
    }

    /** Lifecycle-action body — reason is optional, applied to both suspend and reactivate. */
    @io.swagger.v3.oas.annotations.media.Schema(name = "ReasonRequest")
    public record ReasonRequest(@Nullable String reason) {}

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        log.info("Connection lookup 404: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<Map<String, String>> handleEntityMissing(EntityNotFoundException e) {
        log.info("Entity lookup 404: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> handleIllegalTransition(IllegalStateException e) {
        log.warn("Connection lifecycle conflict (409): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> handleDbConflict(DataIntegrityViolationException e) {
        log.warn("Connection DB conflict (409): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            Map.of("error", "Conflict: " + e.getMostSpecificCause().getMessage())
        );
    }
}
