package de.tum.cit.aet.hephaestus.integration.outline.collection;

import de.tum.cit.aet.hephaestus.integration.outline.domain.OutlineCollection.MirrorState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

/**
 * Request to move a mirrored collection to a target mirror state ({@code ENABLED ⇄ PAUSED}).
 * Resuming resets the sync status to {@code PENDING} so the catch-up tick reconverges the
 * frozen mirror; requesting the current state is an idempotent no-op.
 */
@Schema(description = "Transition a mirrored Outline collection to a target mirror state (pause / resume)")
public record UpdateOutlineCollectionStateRequestDTO(
    @NonNull
    @NotNull
    @Schema(description = "Target mirror state", requiredMode = Schema.RequiredMode.REQUIRED)
    MirrorState state
) {}
