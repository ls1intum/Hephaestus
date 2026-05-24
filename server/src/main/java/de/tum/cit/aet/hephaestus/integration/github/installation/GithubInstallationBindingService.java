package de.tum.cit.aet.hephaestus.integration.github.installation;

import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionService.TransitionRequest;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import jakarta.persistence.EntityNotFoundException;
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

    public GithubInstallationBindingService(GithubInstallationUnboundRepository unboundRepository,
                                            ConnectionRepository connectionRepository,
                                            ConnectionService connectionService,
                                            WorkspaceRepository workspaceRepository) {
        this.unboundRepository = unboundRepository;
        this.connectionRepository = connectionRepository;
        this.connectionService = connectionService;
        this.workspaceRepository = workspaceRepository;
    }

    /**
     * Bind {@code installationId} to {@code workspaceId} and activate the connection.
     *
     * @throws NoSuchElementException        if no unbound row exists for the installation
     * @throws EntityNotFoundException       if the target workspace does not exist
     * @throws IllegalStateException         if the installation is already bound to a different workspace
     * @throws DataIntegrityViolationException on rare DB-level collisions (propagates so callers can map to 409)
     */
    @Transactional
    public Connection bind(long installationId, long workspaceId, String actorRef) {
        GithubInstallationUnbound unbound = unboundRepository.findById(installationId)
            .orElseThrow(() -> new NoSuchElementException(
                "No unbound GitHub installation found for installationId=" + installationId
            ));

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
}
