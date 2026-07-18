package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * One row of the integrations catalog — every kind the manifest registry knows about, joined against
 * whether (and which) Connection this workspace already has for it. Powers the overview page's
 * connect CTAs, which a bare {@code GET /connections} can't (it only returns existing rows).
 */
@Schema(description = "Integration kind availability + connection status for this workspace")
public record IntegrationCatalogEntryDTO(
    @NonNull @Schema(description = "Integration kind") IntegrationKind kind,
    @NonNull @Schema(description = "Human-readable display name") String displayName,
    @NonNull
    @Schema(description = "Whether this workspace has a (non-UNINSTALLED) connection for this kind")
    Boolean connected,
    @Schema(description = "Connection id, if connected") Long connectionId,
    @Schema(description = "Connection state, if connected") IntegrationState connectionState
) {}
