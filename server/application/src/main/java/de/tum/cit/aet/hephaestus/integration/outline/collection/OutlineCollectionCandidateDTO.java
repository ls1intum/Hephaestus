package de.tum.cit.aet.hephaestus.integration.outline.collection;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "An Outline collection the token can see, offered in the add-collection picker")
public record OutlineCollectionCandidateDTO(
        @NonNull @Schema(description = "Outline collection id (UUID)")
        String collectionId,

        @Schema(description = "Collection name as shown in Outline") @Nullable
        String name,

        @Schema(description = "Outline url id (the short slug in collection URLs)") @Nullable
        String urlId,

        @Schema(description = "Collection color as configured in Outline") @Nullable
        String color,

        @Schema(description = "Collection icon as configured in Outline") @Nullable
        String icon,

        @Schema(description = "Collection description as shown in Outline") @Nullable
        String description,

        @NonNull @Schema(description = "Whether this collection is already registered for mirroring")
        Boolean alreadyMirrored) {}
