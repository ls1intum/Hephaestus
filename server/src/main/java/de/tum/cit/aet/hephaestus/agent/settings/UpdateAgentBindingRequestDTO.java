package de.tum.cit.aet.hephaestus.agent.settings;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * PUT body to bind (or unbind) an agent config to a workspace purpose (practice detection / mentor).
 * {@code configId == null} unbinds; a non-null id must reference a config in this workspace (else 404).
 */
@Schema(description = "Bind an agent config to a workspace purpose; null unbinds")
public record UpdateAgentBindingRequestDTO(
    @Schema(description = "Agent config id to bind, or null to unbind") @Nullable Long configId
) {}
