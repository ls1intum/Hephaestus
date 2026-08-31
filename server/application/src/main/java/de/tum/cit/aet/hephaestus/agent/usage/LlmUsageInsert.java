package de.tum.cit.aet.hephaestus.agent.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Immutable row passed to the ledger's idempotent native insert: one component per column of
 * {@link LlmUsageEvent}, named after the column and in the column's order, which
 * {@code LlmUsageInsertContractTest} enforces position by position.
 */
public record LlmUsageInsert(
        UUID id,
        Long workspaceId,
        String jobType,
        String sourceType,
        UUID sourceId,
        int sourceAttempt,
        @Nullable String model,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long reasoningTokens,
        int totalCalls,
        @Nullable BigDecimal costUsd,
        Instant occurredAt,
        String pricingState,
        String fundingSource,
        @Nullable Long appliedPriceId,
        @Nullable Long appliedWorkspaceModelId,
        @Nullable BigDecimal appliedPer1mInputUsd,
        @Nullable BigDecimal appliedPer1mOutputUsd,
        @Nullable BigDecimal appliedPer1mCacheReadUsd,
        @Nullable BigDecimal appliedPer1mCacheWriteUsd,
        String usageProvenance) {}
