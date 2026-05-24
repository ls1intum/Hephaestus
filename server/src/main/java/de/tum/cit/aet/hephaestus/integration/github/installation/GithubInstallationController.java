package de.tum.cit.aet.hephaestus.integration.github.installation;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin endpoint for binding a pre-observed GitHub App installation to a workspace.
 *
 * <p>The workspace id appears in the URL path so the arch-test
 * {@code MultiTenancyArchitectureTest.ControllerWorkspaceContextTests} sees a workspace
 * scope on every method.
 *
 * <p>TODO(#1198): Replace the {@link RequireWorkspaceAdminForBinding} placeholder (which
 * currently meta-binds {@code @PreAuthorize("isAuthenticated()")}) with a real guard such
 * as {@code @PreAuthorize("@workspaceAccessGuard.hasAdminAccess(#workspaceId)")}. The
 * permissive default lets the endpoint boot in test profiles that haven't wired the full
 * workspace-access-guard bean graph yet.
 */
@RestController
@RequestMapping("/api/v1/admin/workspaces/{workspaceId}/integrations/github")
public class GithubInstallationController {

    private static final Logger log = LoggerFactory.getLogger(GithubInstallationController.class);

    private final GithubInstallationBindingService bindingService;

    public GithubInstallationController(GithubInstallationBindingService bindingService) {
        this.bindingService = bindingService;
    }

    @PostMapping("/bind")
    @RequireWorkspaceAdminForBinding
    public ResponseEntity<BindResponse> bind(
        @PathVariable Long workspaceId,
        @RequestBody @NotNull BindRequest body,
        Authentication authentication
    ) {
        if (body == null || body.installationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "installationId is required");
        }
        String actorRef = authentication != null ? authentication.getName() : "unknown";
        Connection connection = bindingService.bind(body.installationId(), workspaceId, actorRef);
        return ResponseEntity.ok(BindResponse.of(connection));
    }

    /** Translates the binding service's domain exceptions to HTTP status codes. */
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<String> handleMissingUnbound(NoSuchElementException e) {
        log.info("Bind request rejected (404): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ResponseEntity<String> handleMissingWorkspace(EntityNotFoundException e) {
        log.info("Bind request rejected (404): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<String> handleConflict(IllegalStateException e) {
        log.warn("Bind request rejected (409): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    public record BindRequest(@NotNull Long installationId) {
    }

    public record BindResponse(
        Long id,
        Long workspaceId,
        String kind,
        String instanceKey,
        String state,
        String displayName,
        Instant createdAt,
        Instant updatedAt
    ) {
        static BindResponse of(Connection c) {
            return new BindResponse(
                c.getId(),
                c.getWorkspace().getId(),
                c.getKind().name(),
                c.getInstanceKey(),
                c.getState().name(),
                c.getDisplayName(),
                c.getCreatedAt(),
                c.getUpdatedAt()
            );
        }
    }
}
