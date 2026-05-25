package de.tum.cit.aet.hephaestus.integration.github.installation;

import de.tum.cit.aet.hephaestus.integration.github.installation.GithubInstallationBindingService.InstallationExpiredException;
import de.tum.cit.aet.hephaestus.integration.github.installation.GithubInstallationBindingService.InstallerIdentityMismatchException;
import de.tum.cit.aet.hephaestus.integration.github.installation.GithubInstallationBindingService.InstallerIdentityNotLinkedException;
import de.tum.cit.aet.hephaestus.integration.github.installation.GithubInstallationBindingService.LegacyUnboundRowException;
import de.tum.cit.aet.hephaestus.integration.identity.HephaestusUser;
import de.tum.cit.aet.hephaestus.integration.identity.HephaestusUserRepository;
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
 * <p>TODO(#1198 follow-up): {@link RequireWorkspaceAdminForBinding} is permissive
 * ({@code isAuthenticated()}). Tightening it to {@code @workspaceSecure.isAdminOfWorkspace(#workspaceId)}
 * needs a parameterised SpEL on {@code WorkspaceSecurityExpressions} that resolves the
 * workspace by id directly (without {@code WorkspaceContextHolder}, which is only populated
 * on {@code /workspaces/{slug}/...} paths). See the annotation's Javadoc for the gap details.
 */
@RestController
@RequestMapping("/api/v1/admin/workspaces/{workspaceId}/integrations/github")
public class GithubInstallationController {

    private static final Logger log = LoggerFactory.getLogger(GithubInstallationController.class);

    private final GithubInstallationBindingService bindingService;
    private final HephaestusUserRepository userRepository;

    public GithubInstallationController(GithubInstallationBindingService bindingService,
                                        HephaestusUserRepository userRepository) {
        this.bindingService = bindingService;
        this.userRepository = userRepository;
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
        if (authentication == null || authentication.getName() == null) {
            // Defensive: @RequireWorkspaceAdminForBinding already gates on isAuthenticated().
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
        }
        HephaestusUser user = userRepository.findByKeycloakSubject(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "no HephaestusUser for authenticated principal"));
        Connection connection = bindingService.bind(body.installationId(), workspaceId, user);
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

    @ExceptionHandler(InstallerIdentityNotLinkedException.class)
    ResponseEntity<String> handleNotLinked(InstallerIdentityNotLinkedException e) {
        // 412 PRECONDITION_REQUIRED says "do this preparatory step (link your GitHub
        // identity) and retry". Distinct from 403 so the UI can show a link CTA.
        log.info("Bind request rejected (412): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(e.getMessage());
    }

    @ExceptionHandler(InstallerIdentityMismatchException.class)
    ResponseEntity<String> handleIdentityMismatch(InstallerIdentityMismatchException e) {
        // Opaque message + 403; do NOT reveal whether the installer id is claimable.
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    @ExceptionHandler(InstallationExpiredException.class)
    ResponseEntity<String> handleExpired(InstallationExpiredException e) {
        log.info("Bind request rejected (410): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.GONE).body(e.getMessage());
    }

    @ExceptionHandler(LegacyUnboundRowException.class)
    ResponseEntity<String> handleLegacy(LegacyUnboundRowException e) {
        log.warn("Bind request rejected (409 legacy row): {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
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
