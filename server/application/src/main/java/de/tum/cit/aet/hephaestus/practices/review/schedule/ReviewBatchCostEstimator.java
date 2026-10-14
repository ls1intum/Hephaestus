package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import java.math.BigDecimal;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewBatchCostEstimator {
    private final ReviewRepository reviews;
    private final ReviewBatchProperties properties;
    private final Clock clock;

    public @Nullable BigDecimal meanCostPerReviewUsd(long ws, String itemKind) {
        return reviews.meanCostPerReviewUsd(ws, itemKind, clock.instant().minus(properties.costHistoryWindow()));
    }

    public @Nullable BigDecimal estimateTotalUsd(long ws, String itemKind, int count) {
        var mean = meanCostPerReviewUsd(ws, itemKind);
        return mean == null ? null : mean.multiply(BigDecimal.valueOf(count));
    }
}
