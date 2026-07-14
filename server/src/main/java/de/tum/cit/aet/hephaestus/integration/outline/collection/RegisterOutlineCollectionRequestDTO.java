package de.tum.cit.aet.hephaestus.integration.outline.collection;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;

/**
 * Request to register an Outline collection for mirroring. The id is verified against the live
 * {@code collections.list} before a row is created; registration is idempotent on the natural key
 * {@code (workspace, connection, collectionId)}.
 */
@Schema(description = "Register an Outline collection for mirroring (lands ENABLED + PENDING)")
public record RegisterOutlineCollectionRequestDTO(
    @NonNull
    @NotBlank
    @Size(max = 64)
    @Schema(description = "Outline collection id (UUID)", requiredMode = Schema.RequiredMode.REQUIRED)
    String collectionId
) {}
