package de.tum.cit.aet.hephaestus.practices.curated.adoption;

import de.tum.cit.aet.hephaestus.practices.curated.dto.CuratedGroupRequestDTO;
import java.util.List;
import org.jspecify.annotations.NonNull;

public record CatalogGroupAdoptionPreviewDTO(
        @NonNull String slug,
        @NonNull CuratedGroupRequestDTO definition,
        @NonNull CatalogGroupDisposition disposition,
        @NonNull List<CatalogPracticePreviewDTO> practices,
        @NonNull List<CatalogGroupPracticeActionDTO> actions,
        @NonNull String etag) {}
