package de.tum.cit.aet.hephaestus.practices.curated.dto;

import java.util.List;
import org.jspecify.annotations.NonNull;

public record CuratedPracticeCatalogDTO(
    @NonNull List<CuratedPracticeAreaDTO> areas,
    @NonNull List<CuratedPracticeSummaryDTO> practices
) {}
