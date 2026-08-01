package de.tum.cit.aet.hephaestus.practices.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * Where a workspace's practice or area came from, and whether it has drifted from it.
 *
 * <p>A workspace's copies are its own and the instance never rewrites them. This is what keeps that
 * honest rather than silent: the workspace can see that it is running an older definition, or one it
 * has changed itself, and see when the instance no longer offers the entry at all.
 */
@Schema(description = "The catalog entry a workspace copy came from, and how far it has drifted")
public record CatalogOriginDTO(
    @NonNull @Schema(description = "Slug of the catalog entry this copy was made from") String slug,
    @NonNull CatalogLink link,
    @NonNull @Schema(description = "Whether the instance still offers this entry to workspaces") Boolean sourceOffered
) {}
