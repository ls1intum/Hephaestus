package de.tum.cit.aet.hephaestus.practices.curated.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;

public record CuratedCatalogDTO(
    @NonNull @Schema(description = "Strong entity tag to send in If-Match when reordering this catalog") String etag,
    @NonNull Boolean customOrder,
    @NonNull CuratedCatalogSummaryDTO summary,
    @NonNull List<CuratedAreaDTO> areas,
    @NonNull List<CuratedPracticeSummaryDTO> practices
) {}
