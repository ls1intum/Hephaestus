package de.tum.cit.aet.hephaestus.practices.spi;

import de.tum.cit.aet.hephaestus.integration.core.spi.UsagePricingState;
import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ReviewUsageAttempt(
        UUID jobId,
        int attempt,
        long calls,
        long inputTokens,
        long outputTokens,
        long reasoningTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        @Nullable BigDecimal costUsd,
        UsagePricingState pricingState) {}
