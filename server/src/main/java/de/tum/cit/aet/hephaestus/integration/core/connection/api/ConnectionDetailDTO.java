package de.tum.cit.aet.hephaestus.integration.core.connection.api;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.framework.IntegrationManifestRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationFamily;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import org.springframework.lang.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Detailed view of a single Connection — extends {@link ConnectionSummaryDTO} with the
 * typed config serialized as a tree node. NEVER carries credentials; the encrypted
 * blob stays inside the entity and is not exposed by this DTO.
 *
 * <p>Mirroring the summary fields (rather than embedding the summary record) keeps
 * the JSON shape flat — the API consumer sees one record, not a nested {@code summary}
 * object.
 */
public record ConnectionDetailDTO(
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
    Set<Capability> capabilities,
    @Nullable @Schema(type = "object", description = "Opaque, typed connection config tree (no credentials).") JsonNode config
) {
    public static ConnectionDetailDTO from(Connection c, IntegrationManifestRegistry manifests, ObjectMapper mapper) {
        JsonNode configNode = c.getConfig() == null ? null : mapper.valueToTree(c.getConfig());
        return new ConnectionDetailDTO(
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
            manifests.capabilitiesFor(c.getKind()),
            configNode
        );
    }
}
