package de.tum.cit.aet.hephaestus.agent.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

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
    String model,
    long inputTokens,
    long outputTokens,
    long cacheReadTokens,
    long cacheWriteTokens,
    long reasoningTokens,
    int totalCalls,
    BigDecimal costUsd,
    Instant occurredAt,
    String pricingState,
    String fundingSource,
    Long appliedPriceId,
    Long appliedWorkspaceModelId,
    BigDecimal appliedPer1mInputUsd,
    BigDecimal appliedPer1mOutputUsd,
    BigDecimal appliedPer1mCacheReadUsd,
    BigDecimal appliedPer1mCacheWriteUsd
) {}
