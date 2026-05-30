package de.tum.cit.aet.hephaestus.integration.core.connection.api;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.framework.IntegrationManifestRegistry;
import de.tum.cit.aet.hephaestus.integration.core.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationFamily;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.ObjectMapper;

/**
 * Detailed view of a single Connection — extends {@link ConnectionSummaryDTO} with the
 * sealed config serialized to a free-form JSON object ({@code Map<String, Object>} →
 * {@code type: object, additionalProperties: true} in the spec, so it round-trips through
 * client codegen). NEVER carries credentials; the encrypted blob stays inside the entity
 * and is not exposed by this DTO.
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
    @Nullable Map<String, Object> config
) {
    @SuppressWarnings("unchecked")
    public static ConnectionDetailDTO from(Connection c, IntegrationManifestRegistry manifests, ObjectMapper mapper) {
        Map<String, Object> configMap = c.getConfig() == null ? null : mapper.convertValue(c.getConfig(), Map.class);
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
            configMap
        );
    }
}
