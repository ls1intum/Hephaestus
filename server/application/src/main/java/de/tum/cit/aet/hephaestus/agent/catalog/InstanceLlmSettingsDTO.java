package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "Instance-wide LLM governance settings")
public record InstanceLlmSettingsDTO(
        @Schema(
                description = "Comma/newline-delimited egress host allowlist; blank = allow any public host",
                example = "api.openai.com, llm-gateway.example.com")
        @Nullable
        String allowedEgressHosts,

        @NonNull @Schema(description = "Whether workspaces may register their own LLM connections")
        Boolean allowWorkspaceConnections) {
    public static InstanceLlmSettingsDTO from(InstanceLlmSettings settings) {
        return new InstanceLlmSettingsDTO(settings.getAllowedEgressHosts(), settings.isAllowWorkspaceConnections());
    }
}
