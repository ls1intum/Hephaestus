package de.tum.cit.aet.hephaestus.practices.spi;

import java.math.BigDecimal;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record ReviewUsageSnapshot(
        @Nullable BigDecimal costUsd,
        boolean priced,
        long calls,
        long inputTokens,
        long outputTokens,
        List<ReviewUsageAttempt> attempts) {
    public ReviewUsageSnapshot {
        attempts = List.copyOf(attempts);
    }
}
