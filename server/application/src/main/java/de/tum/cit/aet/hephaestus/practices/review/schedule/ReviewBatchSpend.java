package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeQuery;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewBatchSpend {
    private final ReviewRepository reviews;
    private final ReviewRuntimeQuery runtime;

    public Snapshot remaining(ReviewBatch batch) {
        if (batch.getBudgetUsd() == null) return new Snapshot(null, true);
        BigDecimal spent = BigDecimal.ZERO;
        for (var review :
                reviews.findByWorkspaceIdAndBatchIdOrderByCreatedAtAsc(batch.getWorkspaceId(), batch.getId())) {
            var usage = runtime.usage(batch.getWorkspaceId(), review.getId());
            if (!usage.priced() || usage.costUsd() == null) return new Snapshot(null, false);
            spent = spent.add(usage.costUsd());
        }
        return new Snapshot(
                batch.getBudgetUsd().subtract(spent).max(BigDecimal.ZERO).setScale(4, RoundingMode.FLOOR), true);
    }

    public record Snapshot(@Nullable BigDecimal remainingUsd, boolean verified) {
        public boolean exhausted() {
            return !verified || (remainingUsd != null && remainingUsd.compareTo(new BigDecimal("0.0001")) < 0);
        }
    }
}
