package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * The instance's LLM policy as it applies to one workspace — the workspace-scoped, read-only twin of
 * {@link InstanceLlmSettingsDTO}.
 *
 * <p>Read-only by construction: every value here is decided instance-wide by an {@code app_admin}, and
 * a workspace admin needs to know it only to render the right controls. Making it a resource of its own
 * rather than a field bolted onto some list response is what keeps that ownership legible — and gives
 * later per-workspace LLM policy an obvious place to land.
 */
@Schema(description = "The instance LLM policy as it applies to this workspace (read-only)")
public record WorkspaceLlmSettingsDTO(
    @NonNull
    @Schema(description = "Whether this workspace may register its own LLM provider connections")
    Boolean ownProviderAllowed
) {}
