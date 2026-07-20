package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Partial update of the instance LLM settings singleton (#1368). Every field is optional; an absent
 * (null) field keeps its current value.
 */
@Schema(description = "Update instance-wide LLM governance settings (all fields optional)")
public record UpdateInstanceLlmSettingsRequest(
    @Nullable
    @Schema(description = "Comma/newline-delimited egress host allowlist; blank clears it")
    String allowedEgressHosts,
    @Nullable
    @Schema(description = "Whether workspaces may register their own LLM connections")
    Boolean allowWorkspaceConnections,
    @Nullable
    @Pattern(regexp = "WARN|BLOCK", message = "defaultUnpricedPolicy must be WARN or BLOCK")
    @Schema(description = "Default policy for usage reported without a price", allowableValues = { "WARN", "BLOCK" })
    String defaultUnpricedPolicy
) {}
