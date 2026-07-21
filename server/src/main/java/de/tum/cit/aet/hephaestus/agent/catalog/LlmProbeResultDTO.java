package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Advisory result of a connection probe (#1368). Always returned with HTTP 200 — a failed upstream is
 * reported as {@code reachable=false} with a message, never raised to the caller. The upstream body is
 * never echoed verbatim; only model ids are surfaced.
 */
@Schema(description = "Result of testing an LLM connection's /models endpoint")
public record LlmProbeResultDTO(
    @NonNull @Schema(description = "Whether the provider answered with a successful models listing") Boolean reachable,
    @NonNull @Schema(description = "Model ids returned by the provider (empty if unreachable)") List<String> models,
    @Nullable @Schema(description = "HTTP status returned by the provider, if any") Integer statusCode,
    @Nullable @Schema(description = "Human-readable diagnostic when not reachable") String message
) {
    public static LlmProbeResultDTO reachable(List<String> models, int statusCode) {
        return new LlmProbeResultDTO(true, models, statusCode, null);
    }

    public static LlmProbeResultDTO unreachable(@Nullable Integer statusCode, String message) {
        return new LlmProbeResultDTO(false, List.of(), statusCode, message);
    }
}
