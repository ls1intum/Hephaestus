package de.tum.cit.aet.hephaestus.agent.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Immutable row passed to the ledger's idempotent native insert.
 *
 * <p>One component per column of {@link LlmUsageEvent}, named after the column and in the column's
 * order. That is not decoration: {@code LlmUsageInsertContractTest} checks the three lists against
 * each other position by position, so a value bound to the wrong column — the one drift a same-typed
 * pair of columns would otherwise hide — fails the build. See that test for why the append cannot
 * simply be derived from the entity.
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
