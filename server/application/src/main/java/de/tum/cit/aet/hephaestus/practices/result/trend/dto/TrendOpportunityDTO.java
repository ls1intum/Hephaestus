package de.tum.cit.aet.hephaestus.practices.result.trend.dto;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.result.trend.TrendBundle;
import de.tum.cit.aet.hephaestus.practices.review.dto.OutcomeVectorDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

public record TrendOpportunityDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int index,
        @NonNull Instant occurredAt,
        @NonNull ItemKind workKind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemId,
        @NonNull OutcomeVectorDTO outcomes,
        @NonNull TrendBundle bundle) {}
