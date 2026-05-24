package de.tum.cit.aet.hephaestus.integration.registry.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.hephaestus.integration.manifest.IntegrationManifestRegistry;
import de.tum.cit.aet.hephaestus.integration.registry.Connection;
import de.tum.cit.aet.hephaestus.integration.spi.Capability;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationFamily;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationState;
import java.time.Instant;
import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * Detailed view of a single Connection — extends {@link ConnectionSummary} with the
 * typed config serialized as a tree node. NEVER carries credentials; the encrypted
 * blob stays inside the entity and is not exposed by this DTO.
 *
 * <p>Mirroring the summary fields (rather than embedding the summary record) keeps
 * the JSON shape flat — the API consumer sees one record, not a nested {@code summary}
 * object.
 */
public record ConnectionDetail(
    Long id,
    IntegrationKind kind,
    IntegrationFamily.Family family,
    IntegrationState state,
    @Nullable String instanceKey,
    @Nullable String displayName,
    @Nullable String stateReason,
    Instant createdAt,
    Instant updatedAt,
    @Nullable Instant lastActivityAt,
    Set<Capability> capabilities,
    @Nullable JsonNode config
) {

    public static ConnectionDetail from(Connection c, IntegrationManifestRegistry manifests, ObjectMapper mapper) {
        JsonNode configNode = c.getConfig() == null ? null : mapper.valueToTree(c.getConfig());
        return new ConnectionDetail(
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
