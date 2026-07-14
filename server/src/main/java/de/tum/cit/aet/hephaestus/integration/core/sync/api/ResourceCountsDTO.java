package de.tum.cit.aet.hephaestus.integration.core.sync.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "Resource-level rollup for the connection overview badge")
public record ResourceCountsDTO(
    @NonNull @Schema(description = "Total resources known to this connection") Long total,
    @NonNull @Schema(description = "Resources currently reporting a sync error") Long errored
) {
    public static final ResourceCountsDTO ZERO = new ResourceCountsDTO(0L, 0L);
}
