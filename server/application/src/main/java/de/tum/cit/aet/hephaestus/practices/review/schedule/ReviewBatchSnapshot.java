package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchStatus;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record ReviewBatchSnapshot(
        String itemKind,
        ReviewBatchOrigin origin,
        @Nullable Instant from,
        @Nullable Instant to,
        ReviewBatchStatus status,
        int estimatedItems,
        @Nullable BigDecimal estimatedCostUsd)
        implements ConfigAuditSnapshot {
    static ReviewBatchSnapshot of(ReviewBatch r) {
        return new ReviewBatchSnapshot(
                r.getKind(),
                r.getOrigin(),
                r.getFromAt(),
                r.getToAt(),
                r.getStatus(),
                r.getEstimatedItems(),
                r.getEstimatedCostUsd());
    }
}
