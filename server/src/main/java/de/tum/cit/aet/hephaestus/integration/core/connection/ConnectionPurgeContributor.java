package de.tum.cit.aet.hephaestus.integration.core.connection;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Plugs into the existing {@code WorkspacePurgeContributor} SPI to cascade workspace
 * PURGE into Connection lifecycle transitions.
 *
 * <p>{@code WorkspaceStatus.PURGED} is soft-delete, so {@code ON DELETE CASCADE} on
 * {@code connection.workspace_id} would NOT fire. We explicitly transition every
 * still-ACTIVE / SUSPENDED Connection to UNINSTALLED, preserving audit history.
 *
 * <p>Audit + transition idempotency means re-running purge against a PURGED workspace
 * (e.g. retry after a partial failure) is a no-op.
 */
@Component
public class ConnectionPurgeContributor implements WorkspacePurgeContributor {

    private final ConnectionRepository connectionRepository;
    private final ConnectionService connectionService;

    public ConnectionPurgeContributor(ConnectionRepository connectionRepository, ConnectionService connectionService) {
        this.connectionRepository = connectionRepository;
        this.connectionService = connectionService;
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        List<Connection> connections = connectionRepository.findByWorkspaceId(workspaceId);
        for (Connection c : connections) {
            if (c.getState() == IntegrationState.UNINSTALLED) {
                continue;
            }
            connectionService.transition(
                c,
                new ConnectionService.TransitionRequest(
                    IntegrationState.UNINSTALLED,
                    "WORKSPACE_PURGED",
                    "SYSTEM",
                    "workspace-purge",
                    "workspace-" + workspaceId + "-purge-" + c.getId(),
                    "Cascade from workspace PURGE"
                )
            );
        }
    }

    @Override
    public int getOrder() {
        // Run early — downstream modules (feedback, audit) cascade off Connection.
        return -100;
    }
}
