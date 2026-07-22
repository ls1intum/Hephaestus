package de.tum.cit.aet.hephaestus.agent.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable row passed to the ledger's idempotent native insert. */
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
    BigDecimal inputRate,
    BigDecimal outputRate,
    BigDecimal cacheReadRate,
    BigDecimal cacheWriteRate
) {}
