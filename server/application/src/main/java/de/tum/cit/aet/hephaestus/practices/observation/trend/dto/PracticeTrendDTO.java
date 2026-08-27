package de.tum.cit.aet.hephaestus.practices.observation.trend.dto;

import de.tum.cit.aet.hephaestus.practices.observation.trend.TrendDirection;
import de.tum.cit.aet.hephaestus.practices.observation.trend.TrendScope;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public record PracticeTrendDTO(
    @NonNull String slug,
    @NonNull TrendScope scope,
    @NonNull TrendDirection direction,
    @NonNull TrendSupportDTO support,
    @Nullable OutcomeVectorDTO currentOutcomes,
    @Nullable OutcomeVectorDTO previousOutcomes,
    @NonNull List<TrendOpportunityDTO> opportunities
) {}
