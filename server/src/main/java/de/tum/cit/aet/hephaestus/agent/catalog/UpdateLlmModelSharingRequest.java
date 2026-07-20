package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Share a model with workspaces (#1368). {@code PUBLIC} shares with all workspaces and clears any
 * existing grants; {@code GRANTED} shares only with {@code workspaceIds}, replacing the current set.
 */
@Schema(description = "Share with: all workspaces, or a selected set")
public record UpdateLlmModelSharingRequest(
    @NonNull
    @NotNull
    @Schema(description = "Share with all workspaces (PUBLIC) or only the selected ones (GRANTED)")
    ModelVisibility visibility,
    @Nullable
    @Schema(description = "Workspace ids to share with; required and used only when visibility is GRANTED")
    List<Long> workspaceIds
) {}
