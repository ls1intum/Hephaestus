package de.tum.cit.aet.hephaestus.workspace;

import de.tum.cit.aet.hephaestus.integration.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.connection.ConnectionService.TransitionRequest;
import de.tum.cit.aet.hephaestus.integration.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provisions an ACTIVE PAT-backed {@link Connection} row for a workspace.
 *
 * <p>Shared across the two paths that create PAT workspaces from scratch:
 * {@link WorkspaceService#createWorkspace(de.tum.cit.aet.hephaestus.workspace.dto.CreateWorkspaceRequestDTO)}
 * (user-driven via the REST API) and {@link WorkspaceProvisioningService} bootstrap
 * (config-driven local dev). Extracted into its own component so both callers can
 * reuse the same upsert + audit-row + credential-rotation sequence without bloating
 * their host services past the architecture ceiling on dependency count.
 *
 * <p>Idempotent: re-running with the same {@code (workspace, kind, instanceKey)}
 * triple re-uses the existing row and rotates the credential — no duplicate row,
 * no audit flap.
 */
@Component
public class ScmConnectionProvisioner {

    private final ConnectionRepository connectionRepository;
    private final ConnectionService connectionService;

    public ScmConnectionProvisioner(ConnectionRepository connectionRepository,
                                    ConnectionService connectionService) {
        this.connectionRepository = connectionRepository;
        this.connectionService = connectionService;
    }

    /**
     * Upsert + activate the workspace's PAT Connection row.
     *
     * @param workspace      target workspace
     * @param kind           {@link IntegrationKind#GITHUB} or {@link IntegrationKind#GITLAB}
     * @param instanceKey    "pat" for GitHub PAT mode, server URL for GitLab. Must be
     *                       stable across re-runs so the unique constraint dedupes.
     * @param config         the {@link ConnectionConfig} variant to persist
     * @param token          PAT token; may be blank to skip credential rotation
     * @param correlationId  audit correlation key; webhook redelivery is idempotent on this id
     */
    @Transactional
    public void provisionPatConnection(Workspace workspace,
                                       IntegrationKind kind,
                                       String instanceKey,
                                       ConnectionConfig config,
                                       String token,
                                       String correlationId) {
        Connection connection = connectionRepository
            .findByWorkspaceIdAndKindAndInstanceKey(workspace.getId(), kind, instanceKey)
            .orElseGet(() -> {
                Connection fresh = new Connection(workspace, kind, instanceKey, config);
                fresh.setDisplayName(workspace.getAccountLogin());
                return connectionRepository.save(fresh);
            });

        if (connection.getState() != IntegrationState.ACTIVE) {
            connection = connectionService.transition(connection, new TransitionRequest(
                IntegrationState.ACTIVE,
                "PAT_PROVISIONED",
                "SYSTEM",
                "scm-connection-provisioner",
                correlationId,
                "Provisioned PAT connection on workspace creation"
            ));
        }

        if (token != null && !token.isBlank()) {
            connectionService.rotateBearerToken(workspace.getId(), kind, new BearerToken(token, null));
        }
    }
}
