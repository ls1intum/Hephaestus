package de.tum.cit.aet.hephaestus.practices.observation.trend.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.observation.trend.TrendBundle;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

public record TrendOpportunityDTO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int index,
        @NonNull Instant occurredAt,
        @NonNull ArtifactKind workKind,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long reviewedWorkId,
        @NonNull OutcomeVectorDTO outcomes,
        @NonNull TrendBundle bundle) {}
