package de.tum.cit.aet.hephaestus.integration.outline.connect;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;

/**
 * Resolves a workspace's ACTIVE Outline connection — the one 404 gate shared by every Outline admin
 * surface ({@link OutlineConnectionAdminService} and the collection registry's admin service). Both
 * previously carried their own copy of this check and had silently diverged: only one of them treated
 * a blank {@code serverUrl} as "not connected".
 *
 * <p>A blank {@code serverUrl} is a real reachable state, not just theoretical: the inline-connect
 * config builder defaults an omitted {@code server_url} to {@code null} and the Connection still
 * reaches ACTIVE (collections/documents live entirely in local tables, so nothing about activation
 * itself requires a server URL). Treating that half-configured install as "not usably connected" (404)
 * is correct — every Outline admin operation eventually calls out to the stored server, so surfacing it
 * as connected would only defer the failure to the first outbound call.
 */
public final class OutlineConnectionResolver {

    private OutlineConnectionResolver() {}

    /** The workspace's ACTIVE, usably-configured Outline connection, or {@link EntityNotFoundException} (404). */
    public static Connection requireActiveConnection(ConnectionService connectionService, long workspaceId) {
        Connection connection = connectionService
            .findActive(workspaceId, IntegrationKind.OUTLINE)
            .orElseThrow(() -> new EntityNotFoundException("Outline connection", Long.toString(workspaceId)));
        if (
            !(connection.getConfig() instanceof ConnectionConfig.OutlineConfig config) ||
            config.serverUrl() == null ||
            config.serverUrl().isBlank()
        ) {
            throw new EntityNotFoundException("Outline connection", Long.toString(workspaceId));
        }
        return connection;
    }
}
