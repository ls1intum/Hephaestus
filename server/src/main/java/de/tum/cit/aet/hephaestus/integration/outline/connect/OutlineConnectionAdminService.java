package de.tum.cit.aet.hephaestus.integration.outline.connect;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiClient;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Admin surface behind the Outline connection endpoints, scoped to the kind-specific credential
 * concern (the token health probe). The health snapshot and manual "sync now" trigger live in the
 * unified sync-observability API: {@code OutlineConnectionSyncStateProvider} (read) and
 * {@code OutlineIntegrationSyncRunner} (manual trigger); the per-connection job guard in
 * {@code SyncJobService} owns in-flight de-duplication.
 */
@Service
@ConditionalOnServerRole
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineConnectionAdminService {

    private static final Logger log = LoggerFactory.getLogger(OutlineConnectionAdminService.class);

    private final ConnectionService connectionService;
    private final OutlineApiClient apiClient;

    public OutlineConnectionAdminService(ConnectionService connectionService, OutlineApiClient apiClient) {
        this.connectionService = connectionService;
        this.apiClient = apiClient;
    }

    /**
     * Probes the stored API token against Outline: {@code auth.info} answers whether the token is still
     * accepted at all, and {@code apiKeys.list} — when the token may see its own key — adds the name,
     * expiry and last-use Outline keeps for it. A rejected token yields {@code accepted=false} rather
     * than an error: "your token no longer works" is exactly the answer the admin card came to ask.
     */
    public OutlineTokenStatusDTO tokenStatus(long workspaceId) {
        Connection connection = requireActiveConnection(workspaceId);
        ConnectionConfig.OutlineConfig config = (ConnectionConfig.OutlineConfig) connection.getConfig();
        String token = connectionService
            .findActiveBearerToken(workspaceId, IntegrationKind.OUTLINE)
            .map(BearerToken::token)
            .orElse(null);
        if (token == null) {
            return new OutlineTokenStatusDTO(false, null, null, null, null);
        }
        try {
            apiClient.validateToken(config.serverUrl(), token);
        } catch (OutlineApiException e) {
            log.debug("outline.admin: token probe rejected for workspaceId={}: {}", workspaceId, e.toString());
            return new OutlineTokenStatusDTO(false, null, null, null, null);
        }
        try {
            return apiClient
                .describeToken(config.serverUrl(), token)
                .map(d -> new OutlineTokenStatusDTO(true, d.name(), d.last4(), d.expiresAt(), d.lastActiveAt()))
                .orElseGet(() -> new OutlineTokenStatusDTO(true, null, null, null, null));
        } catch (OutlineApiException e) {
            // The token is accepted (auth.info passed); only the metadata probe faltered — a flaky
            // apiKeys.list must not turn a healthy token into a 502. Treat it like the 403 case:
            // token accepted, metadata unavailable.
            log.debug(
                "outline.admin: token accepted but metadata probe failed for workspaceId={}: {}",
                workspaceId,
                e.toString()
            );
            return new OutlineTokenStatusDTO(true, null, null, null, null);
        }
    }

    /** The workspace's ACTIVE Outline connection, or a 404 when not connected — see {@link OutlineConnectionResolver}. */
    private Connection requireActiveConnection(long workspaceId) {
        return OutlineConnectionResolver.requireActiveConnection(connectionService, workspaceId);
    }
}
