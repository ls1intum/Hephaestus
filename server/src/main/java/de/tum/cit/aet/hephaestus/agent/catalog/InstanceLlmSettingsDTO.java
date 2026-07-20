package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

/** Instance-wide LLM governance settings (#1368). GLOBAL, {@code app_admin}-owned. */
@Schema(description = "Instance-wide LLM governance settings")
public record InstanceLlmSettingsDTO(
    @Schema(
        description = "Comma/newline-delimited egress host allowlist; blank = allow any public host",
        example = "api.openai.com, api.anthropic.com"
    )
    String allowedEgressHosts,
    @NonNull
    @Schema(description = "Whether workspaces may register their own LLM connections")
    Boolean allowWorkspaceConnections,
    @NonNull
    @Schema(description = "Default policy for usage reported without a price", example = "WARN")
    String defaultUnpricedPolicy
) {
    public static InstanceLlmSettingsDTO from(InstanceLlmSettings settings) {
        return new InstanceLlmSettingsDTO(
            settings.getAllowedEgressHosts(),
            settings.isAllowWorkspaceConnections(),
            settings.getDefaultUnpricedPolicy()
        );
    }
}
