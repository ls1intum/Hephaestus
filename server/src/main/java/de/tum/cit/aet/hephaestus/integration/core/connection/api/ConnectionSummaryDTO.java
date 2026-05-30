package de.tum.cit.aet.hephaestus.integration.core.connection.api;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.framework.IntegrationManifestRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationFamily;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import java.time.Instant;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Wire shape returned by the {@code GET /api/v1/workspaces/{workspaceId}/connections}
 * list + by lifecycle endpoints (suspend, reactivate). Lightweight — no config, no
 * credentials, no audit. Capabilities are looked up from the per-kind manifest at
 * response build time so adding/removing a capability needs no DB migration.
 */
public record ConnectionSummaryDTO(
    Long id,
    IntegrationKind kind,
    IntegrationFamily family,
    IntegrationState state,
    @Nullable String instanceKey,
    @Nullable String displayName,
    @Nullable String stateReason,
    Instant createdAt,
    Instant updatedAt,
    @Nullable Instant lastActivityAt,
    Set<Capability> capabilities
) {
    public static ConnectionSummaryDTO from(Connection c, IntegrationManifestRegistry manifests) {
        return new ConnectionSummaryDTO(
            c.getId(),
            c.getKind(),
            c.getKind().family(),
            c.getState(),
            c.getInstanceKey(),
            c.getDisplayName(),
            c.getStateReason(),
            c.getCreatedAt(),
            c.getUpdatedAt(),
            c.getLastActivityAt(),
            manifests.capabilitiesFor(c.getKind())
        );
    }
}
