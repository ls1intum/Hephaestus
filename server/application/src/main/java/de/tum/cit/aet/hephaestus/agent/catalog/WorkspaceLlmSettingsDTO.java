package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/**
 * The instance's LLM policy as it applies to one workspace — the workspace-scoped, read-only twin of
 * {@link InstanceLlmSettingsDTO}.
 */
@Schema(description = "The instance LLM policy as it applies to this workspace (read-only)")
public record WorkspaceLlmSettingsDTO(
        @NonNull @Schema(description = "Whether this workspace may register its own LLM provider connections")
        Boolean ownProviderAllowed) {}
