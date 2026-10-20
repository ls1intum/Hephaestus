package de.tum.cit.aet.hephaestus.agent.usage;

import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The measurement a ledger row of a review job attempt takes when the attempt is projected again: every column
 * that {@link LlmUsageEventRepository#remeasure} may change, and no other. Source, model, funding source, rates
 * and date are not here, so a projection cannot change them.
 */
public record LlmUsageRemeasurement(
        UUID id,
        Long workspaceId,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long reasoningTokens,
        long searchUnits,
        int totalCalls,
        int unreportedCalls,
        String usageProvenance,
        String measurementVerification,
        String pricingState,
        @Nullable BigDecimal costUsd) {}
