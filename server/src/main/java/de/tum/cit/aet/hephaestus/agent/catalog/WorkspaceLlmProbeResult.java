package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Workspace-framed "Test connection" result (#1368): answers only "does my key work?" — {@code
 * reachable} plus how many models the provider listed. Deliberately narrower than {@link
 * LlmProbeResult}: the raw model id list is never surfaced to a workspace admin, only the count (see
 * the LLM-config glossary: "Connected — N models available", never a raw dump).
 */
@Schema(description = "Result of testing your AI provider connection")
public record WorkspaceLlmProbeResult(
    @NonNull @Schema(description = "Whether the provider answered") Boolean reachable,
    @NonNull @Schema(description = "How many models the provider listed (0 if unreachable)") Integer modelCount,
    @Nullable @Schema(description = "Human-readable diagnostic when not reachable") String message
) {
    static WorkspaceLlmProbeResult from(LlmProbeResult raw) {
        if (!raw.reachable()) {
            return new WorkspaceLlmProbeResult(false, 0, raw.message());
        }
        return new WorkspaceLlmProbeResult(true, raw.models().size(), null);
    }
}
