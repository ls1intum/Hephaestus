package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record PracticeScriptModelDTO(
        ModelKind kind,
        @Nullable String modelId,
        @Nullable DataHandlingTier dataHandlingTier,
        long calls,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens) {}
