package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedAreaRequestDTO;
import java.util.List;
import org.jspecify.annotations.NonNull;

public record CatalogAreaAdoptionPreviewDTO(
    @NonNull String slug,
    @NonNull CuratedAreaRequestDTO definition,
    @NonNull CatalogAreaDisposition disposition,
    @NonNull List<CatalogPracticePreviewDTO> practices,
    @NonNull List<CatalogAreaPracticeActionDTO> actions,
    @NonNull String etag
) {}
