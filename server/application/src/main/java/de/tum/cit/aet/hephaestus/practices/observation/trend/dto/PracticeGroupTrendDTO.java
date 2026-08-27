package de.tum.cit.aet.hephaestus.practices.observation.trend.dto;

import java.util.List;
import org.jspecify.annotations.NonNull;

public record PracticeGroupTrendDTO(
        @NonNull PracticeTrendDTO group, @NonNull List<PracticeTrendDTO> practices) {}
