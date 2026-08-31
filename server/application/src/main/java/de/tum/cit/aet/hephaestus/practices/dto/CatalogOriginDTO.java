package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

@Schema(description = "The catalog entry a workspace copy came from and whether it differs now")
public record CatalogOriginDTO(
        @NonNull @Schema(description = "Slug of the catalog entry this copy was made from")
        String slug,

        @NonNull CatalogLink link,

        @NonNull @Schema(description = "Whether new workspaces receive the source entry")
        Boolean sourceOffered) {}
