package de.tum.cit.aet.hephaestus.practices.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record ScanExecutionSnapshot(
        ReviewSnapshot review,
        ReviewStatus status,
        BigDecimal budgetUsd,
        Instant createdAt,
        boolean cancellationRequested,
        boolean inventoryRecorded,
        boolean inventoryComplete,
        Set<Long> listedPracticeIds,
        long listed,
        long judged,
        long reused,
        long withheld) {
    public ScanExecutionSnapshot {
        listedPracticeIds = Set.copyOf(listedPracticeIds);
    }
}
