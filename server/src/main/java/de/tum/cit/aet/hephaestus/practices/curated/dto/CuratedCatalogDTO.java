package de.tum.cit.aet.hephaestus.practices.curated.dto;

import java.util.List;
import org.jspecify.annotations.NonNull;

/** The instance catalog: what it offers, and one line describing how it stands. */
public record CuratedCatalogDTO(
    @NonNull String etag,
    @NonNull Boolean customOrder,
    @NonNull CuratedCatalogSummaryDTO summary,
    @NonNull List<CuratedAreaDTO> areas,
    @NonNull List<CuratedPracticeSummaryDTO> practices
) {}
