package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageEventRepository;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageJobType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a campaign is likely to cost, derived from this workspace's own recent review costs rather than a
 * fleet-wide constant — price depends on the model bound, diff size and how many practices are on.
 *
 * <p>Returns {@code null}, not zero, when there is nothing to derive from: a zero on a fresh workspace's
 * confirmation screen would read as "this is free".
 */
@Component
public class ReviewBackfillCostEstimator {

    private final LlmUsageEventRepository usageRepository;
    private final ReviewBackfillProperties properties;

    public ReviewBackfillCostEstimator(LlmUsageEventRepository usageRepository, ReviewBackfillProperties properties) {
        this.usageRepository = usageRepository;
        this.properties = properties;
    }

    /**
     * The mean priced cost of one review of this job type in this workspace, or {@code null} when none of its
     * recent reviews carried a resolvable price.
     *
     * <p>Sums both cost purses — a budget decision must never do that — because a forecast of what a campaign
     * will cost is about the work, not who pays; unpriced runs are excluded from both the numerator and the
     * denominator so a half-priced catalogue is not dragged toward zero. The denominator counts reviews, not
     * usage rows, since a retried review writes multiple rows and this is the number an admin confirms a spend
     * against.
     */
    @Transactional(readOnly = true)
    public @Nullable BigDecimal meanCostPerReviewUsd(Long workspaceId, AgentJobType jobType) {
        return calculateMeanCostPerReviewUsd(workspaceId, jobType);
    }

    private @Nullable BigDecimal calculateMeanCostPerReviewUsd(Long workspaceId, AgentJobType jobType) {
        Instant to = Instant.now();
        Instant from = to.minus(properties.costHistoryWindow());
        LlmUsageEventRepository.ReviewCostAggregate row = usageRepository.aggregateCostPerReview(
            workspaceId,
            LlmUsageJobType.from(jobType).name(),
            from,
            to
        );
        if (row == null || row.getReviews() <= 0) {
            return null;
        }
        BigDecimal total = nullToZero(row.getTotalCostUsd());
        if (total.signum() <= 0) {
            return null;
        }
        return total.divide(BigDecimal.valueOf(row.getReviews()), 6, RoundingMode.HALF_UP);
    }

    /** The campaign's forecast, or {@code null} when the per-review cost is unknown. */
    @Transactional(readOnly = true)
    public @Nullable BigDecimal estimateTotalUsd(Long workspaceId, AgentJobType jobType, int artifacts) {
        BigDecimal perReview = calculateMeanCostPerReviewUsd(workspaceId, jobType);
        if (perReview == null) {
            return null;
        }
        return perReview.multiply(BigDecimal.valueOf(artifacts)).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(@Nullable BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
