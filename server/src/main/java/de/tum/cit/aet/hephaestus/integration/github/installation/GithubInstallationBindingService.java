package de.tum.cit.aet.hephaestus.integration.github.installation;

import de.tum.cit.aet.hephaestus.integration.identity.HephaestusUser;
import de.tum.cit.aet.hephaestus.integration.identity.HephaestusUserRepository;
import de.tum.cit.aet.hephaestus.integration.identity.IntegrationIdentity;
import de.tum.cit.aet.hephaestus.integration.identity.IntegrationIdentityRepository;
import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService.TransitionRequest;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import jakarta.persistence.EntityNotFoundException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Binds a parked GitHub App installation ({@link GithubInstallationUnbound}) to a
 * Hephaestus workspace.
 *
 * <p>Flow:
 * <ol>
 *   <li>Lookup unbound row by installation id.</li>
 *   <li>Reject if the installation is already claimed by a DIFFERENT workspace
 *       (one installation cannot belong to two workspaces).</li>
 *   <li>Create or reuse the {@link Connection} for {@code (workspace, GITHUB, installation_id)},
 *       transition it PENDING → ACTIVE via {@link ConnectionService#transition} so the
 *       audit row + idempotency contract are honoured.</li>
 *   <li>Delete the unbound row in the same transaction — there is no longer any reason
 *       for it to exist.</li>
 * </ol>
 *
 * <p>Cross-workspace collision is enforced at the application layer rather than relying
 * on a DB partial-unique index, since the existing {@code uq_connection} is on the
 * triple {@code (workspace_id, kind, instance_key)} and DOES permit same-instance_key
 * rows across workspaces (intentional for fanout scenarios). A future tightening could
 * add a partial-unique index for the GITHUB kind only, but is out of scope here.
 */
@Service
public class GithubInstallationBindingService {

    private static final Logger log = LoggerFactory.getLogger(GithubInstallationBindingService.class);

    private final GithubInstallationUnboundRepository unboundRepository;
    private final ConnectionRepository connectionRepository;
    private final ConnectionService connectionService;
    private final WorkspaceRepository workspaceRepository;
    private final IntegrationIdentityRepository identityRepository;
    private final HephaestusUserRepository userRepository;

    public GithubInstallationBindingService(GithubInstallationUnboundRepository unboundRepository,
                                            ConnectionRepository connectionRepository,
                                            ConnectionService connectionService,
                                            WorkspaceRepository workspaceRepository,
                                            IntegrationIdentityRepository identityRepository,
                                            HephaestusUserRepository userRepository) {
        this.unboundRepository = unboundRepository;
        this.connectionRepository = connectionRepository;
        this.connectionService = connectionService;
        this.workspaceRepository = workspaceRepository;
        this.identityRepository = identityRepository;
        this.userRepository = userRepository;
    }

    /**
     * Controller-facing overload: resolves the Keycloak subject string from
     * {@code Authentication.getName()} to a {@link HephaestusUser} on this side of the
     * service boundary so the controller stays free of repository dependencies (per
     * {@code ArchitectureTest.controllersDoNotAccessRepositories}).
     *
     * @throws UnknownActorException if no HephaestusUser exists for {@code keycloakSubject}
     */
    @Transactional
    public Connection bindForKeycloakSubject(long installationId, long workspaceId, String keycloakSubject) {
        HephaestusUser user = userRepository.findByKeycloakSubject(keycloakSubject)
            .orElseThrow(() -> new UnknownActorException(
                "No HephaestusUser for authenticated principal — log in via Keycloak before binding"));
        return bind(installationId, workspaceId, user);
    }

    /**
     * Bind {@code installationId} to {@code workspaceId} and activate the connection.
     * Enforces the installer-identity check: the {@code authenticatedUser} MUST have an
     * {@link IntegrationIdentity}({@code GITHUB}, {@code unbound.installer_github_user_id})
     * linked to their {@link HephaestusUser}. Closes the confused-deputy CVE where any
     * authenticated user could claim any observed {@code installation_id}.
     *
     * @throws NoSuchElementException                if no unbound row exists for the installation
     * @throws InstallationExpiredException          if the unbound row passed its 30-day TTL
     * @throws InstallerIdentityNotLinkedException   if the authenticated user has not linked their
     *                                               GitHub identity yet (controller → 412 with link URL)
     * @throws InstallerIdentityMismatchException    if the linked identity does not match the installer
     *                                               (controller → 403 with opaque message — no oracle)
     * @throws LegacyUnboundRowException             if the row predates the installer column
     * @throws EntityNotFoundException               if the target workspace does not exist
     * @throws IllegalStateException                 if the installation is already bound to a different workspace
     * @throws DataIntegrityViolationException       on rare DB-level collisions (propagates so callers can map to 409)
     */
    @Transactional
    public Connection bind(long installationId, long workspaceId, HephaestusUser authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getId() == null) {
            throw new IllegalArgumentException("authenticatedUser is required for bind");
        }
        GithubInstallationUnbound unbound = unboundRepository.findById(installationId)
            .orElseThrow(() -> new NoSuchElementException(
                "No unbound GitHub installation found for installationId=" + installationId
            ));

        if (unbound.getExpiresAt() != null && unbound.getExpiresAt().isBefore(Instant.now())) {
            log.info("Bind rejected (410): unbound row expired installationId={} expiresAt={}",
                installationId, unbound.getExpiresAt());
            throw new InstallationExpiredException(
                "Unbound installation row expired; uninstall and reinstall to retry");
        }

        // Identity check — closes the confused-deputy CVE. The installer's GitHub user id
        // comes from the webhook's `sender.id`; the authenticated Hephaestus user MUST
        // have a linked IntegrationIdentity matching it. Reject otherwise.
        Long installerGithubUserId = unbound.getInstallerGithubUserId();
        if (installerGithubUserId == null) {
            // Legacy row written before the installer column existed — refuse rather than
            // wave through, since the protection isn't enforceable.
            log.warn("Bind rejected (409): legacy unbound row without installer identity, installationId={}",
                installationId);
            throw new LegacyUnboundRowException(
                "Legacy unbound row predates the installer identity check; uninstall and reinstall to bind");
        }
        requireInstallerIdentityMatch(installerGithubUserId, authenticatedUser, installationId, workspaceId);
        String actorRef = authenticatedUser.getKeycloakSubject();

        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Workspace not found: id=" + workspaceId
            ));

        String instanceKey = Long.toString(installationId);

        // Cross-workspace collision check: the same installationId must not be already
        // bound to a different workspace. The legitimate "already bound here" case is
        // permitted — we transition idempotently.
        List<Connection> existing = connectionRepository.findByKindAndInstanceKey(IntegrationKind.GITHUB, instanceKey);
        for (Connection c : existing) {
            if (!c.getWorkspace().getId().equals(workspaceId)) {
                throw new IllegalStateException(
                    "GitHub installation " + installationId + " is already bound to workspace="
                        + c.getWorkspace().getId() + "; refusing to bind to workspace=" + workspaceId
                );
            }
        }

        Connection connection;
        Optional<Connection> ownExisting = connectionRepository
            .findByWorkspaceIdAndKindAndInstanceKey(workspaceId, IntegrationKind.GITHUB, instanceKey);
        if (ownExisting.isPresent()) {
            connection = ownExisting.get();
            log.info(
                "Re-binding existing Connection id={} workspace={} installation={} state={}",
                connection.getId(), workspaceId, installationId, connection.getState()
            );
        } else {
            ConnectionConfig.GitHubAppConfig config = new ConnectionConfig.GitHubAppConfig(
                installationId,
                unbound.getAccountLogin(),
                /* serverUrl */ null,
                Set.of()
            );
            connection = new Connection(workspace, IntegrationKind.GITHUB, instanceKey, config);
            connection.setDisplayName(unbound.getAccountLogin());
            try {
                connection = connectionRepository.save(connection);
            } catch (DataIntegrityViolationException e) {
                // Race: another transaction created the same row between our check and insert.
                // Surface as an IllegalStateException so the controller can return 409.
                log.warn("Concurrent bind detected for installation={} workspace={}: {}",
                    installationId, workspaceId, e.getMessage());
                throw new IllegalStateException(
                    "Concurrent bind for installation=" + installationId + " workspace=" + workspaceId, e
                );
            }
        }

        // Transition PENDING → ACTIVE (or no-op if already ACTIVE). The audit row carries
        // a stable correlation id so a retry of bind is idempotent.
        if (connection.getState() != IntegrationState.ACTIVE) {
            connection = connectionService.transition(connection, new TransitionRequest(
                IntegrationState.ACTIVE,
                "BIND",
                "ADMIN",
                actorRef,
                "bind-" + installationId,
                "Bound from unbound table"
            ));
        }

        // Drop the unbound row — its purpose is served.
        unboundRepository.delete(unbound);
        log.info("Bound GitHub installation {} to workspace {} (connection id={})",
            installationId, workspaceId, connection.getId());

        return connection;
    }

    /**
     * Reject the bind unless an {@link IntegrationIdentity} exists for the installer's
     * GitHub user id AND it links to the authenticated Hephaestus user.
     *
     * <p>Mismatches log at WARN with the installer id HASHED — logging the raw id would
     * help an attacker enumerate which ids are claimable. Successful matches log the raw
     * id at INFO for forensics.
     */
    private void requireInstallerIdentityMatch(long installerGithubUserId,
                                               HephaestusUser authenticatedUser,
                                               long installationId,
                                               long workspaceId) {
        List<IntegrationIdentity> linked = identityRepository.findByKindAndExternalId(
            IntegrationKind.GITHUB, Long.toString(installerGithubUserId));
        // Linked-to-someone case: pick the one with a hephaestus_user. SCM identities are
        // cross-workspace, so there should be at most one.
        Optional<IntegrationIdentity> match = linked.stream()
            .filter(id -> id.getHephaestusUser() != null)
            .findFirst();
        if (match.isEmpty()) {
            log.info("Bind rejected (412): installer GitHub id not linked to any Hephaestus user; "
                + "actor={} workspace={} installerIdHash={}",
                authenticatedUser.getId(), workspaceId, shortHash(installerGithubUserId));
            throw new InstallerIdentityNotLinkedException(
                "Link your GitHub identity before binding this installation");
        }
        HephaestusUser linkedUser = match.get().getHephaestusUser();
        if (!authenticatedUser.getId().equals(linkedUser.getId())) {
            // OPAQUE message — do NOT reveal whether the installer exists; that helps the
            // attacker confirm which installation IDs are claimable. Log at WARN with
            // hashed installer id so SIEM can correlate repeats without a leaked oracle.
            log.warn("Bind rejected (403): installer identity mismatch; actor={} workspace={} "
                + "installerIdHash={}",
                authenticatedUser.getId(), workspaceId, shortHash(installerGithubUserId));
            throw new InstallerIdentityMismatchException("installer_identity_mismatch");
        }
        log.info("Bind identity match OK: actor={} workspace={} installation={} installerGithubUserId={}",
            authenticatedUser.getId(), workspaceId, installationId, installerGithubUserId);
    }

    /** First 8 hex chars of SHA-256(externalId) — defender-correlatable, not enumerable. */
    private static String shortHash(long externalId) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(Long.toString(externalId).getBytes());
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            return "nohash";
        }
    }

    /** 412 — authenticated user has not linked their GitHub identity yet. */
    public static class InstallerIdentityNotLinkedException extends RuntimeException {
        public InstallerIdentityNotLinkedException(String message) { super(message); }
    }

    /** 403 — linked identity does not match the installer. Message stays opaque. */
    public static class InstallerIdentityMismatchException extends RuntimeException {
        public InstallerIdentityMismatchException(String message) { super(message); }
    }

    /** 410 — unbound row passed its 30-day TTL. */
    public static class InstallationExpiredException extends RuntimeException {
        public InstallationExpiredException(String message) { super(message); }
    }

    /** 409 — row predates the installer-identity column. Uninstall + reinstall to bind. */
    public static class LegacyUnboundRowException extends RuntimeException {
        public LegacyUnboundRowException(String message) { super(message); }
    }

    /** 401 — authenticated principal does not map to any HephaestusUser yet. */
    public static class UnknownActorException extends RuntimeException {
        public UnknownActorException(String message) { super(message); }
    }
}
