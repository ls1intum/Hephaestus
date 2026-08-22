package de.tum.cit.aet.hephaestus.practices.observation.trend.dto;

import java.util.List;
import org.jspecify.annotations.NonNull;

public record PracticeAreaTrendDTO(@NonNull PracticeTrendDTO area, @NonNull List<PracticeTrendDTO> practices) {}
