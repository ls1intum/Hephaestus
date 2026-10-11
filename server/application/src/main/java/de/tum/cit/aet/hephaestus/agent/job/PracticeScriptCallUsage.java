package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.catalog.ModelKind;
import de.tum.cit.aet.hephaestus.workspace.spi.DataHandlingTier;
import org.jspecify.annotations.Nullable;

/**
 * One proxied practice script call, as {@link AgentJobRepository#accumulatePracticeScriptUsage} adds it to its row.
 * The buckets are those of the review's own counters: {@code inputTokens} excludes the cache reads and writes, so
 * each is priced at its own rate.
 *
 * @param dataHandlingTier the tier of the model that served the call; {@code null} when the attempt froze no tier for that
 *     model
 * @param searchUnits what a Cohere reranking call billed instead of tokens
 */
public record PracticeScriptCallUsage(
        ModelKind modelKind,
        String practiceSlug,
        @Nullable DataHandlingTier dataHandlingTier,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long searchUnits) {

    /** The stored form of {@link #modelKind()}, for the native query. */
    public String modelKindName() {
        return modelKind.name();
    }

    /** The stored form of {@link #dataHandlingTier()}, for the native query. */
    public @Nullable String dataHandlingTierName() {
        return dataHandlingTier == null ? null : dataHandlingTier.name();
    }
}
