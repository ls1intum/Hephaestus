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
 * What a campaign is likely to cost, derived from what this workspace's reviews have actually cost.
 *
 * <p>Derived rather than configured because a fleet-wide "cost per review" constant would be wrong for
 * every workspace at once: the price depends on the model the workspace bound, the size of its diffs and
 * how many practices it has turned on. The workspace's own recent reviews of the same artifact kind are
 * the closest available predictor.
 *
 * <p>Deliberately returns {@code null}, not zero, when there is nothing to derive from. A fresh workspace
 * — the one most likely to want a backfill — has no history at all, and a zero on the confirmation screen
 * would read as "this is free", which is the single most damaging thing this estimate could say.
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
     * The mean priced cost of one review of this job type in this workspace, or {@code null} when none of
     * its recent reviews carried a resolvable price.
     *
     * <p>Both purses are summed here, which is the one place in the system that is allowed to: a budget
     * decision must never mix them, but a forecast of "what will this campaign cost" is about the work,
     * not about who pays. Unpriced runs are excluded from the denominator as well as the numerator, so an
     * instance with a half-priced catalogue reports the mean of what it could price rather than a mean
     * dragged toward zero by rows it could not.
     */
    @Transactional(readOnly = true)
    public @Nullable BigDecimal meanCostPerReviewUsd(Long workspaceId, AgentJobType jobType) {
        Instant to = Instant.now();
        Instant from = to.minus(properties.costHistoryWindow());
        String wanted = LlmUsageJobType.from(jobType).name();
        for (LlmUsageEventRepository.JobTypeAggregate row : usageRepository.aggregateByJobType(workspaceId, from, to)) {
            if (!wanted.equals(row.getJobType())) {
                continue;
            }
            long priced = row.getEvents() - row.getUnpricedEventCount();
            if (priced <= 0) {
                return null;
            }
            BigDecimal total = nullToZero(row.getPricedTotalCostUsd()).add(nullToZero(row.getByoTotalCostUsd()));
            if (total.signum() <= 0) {
                return null;
            }
            return total.divide(BigDecimal.valueOf(priced), 6, RoundingMode.HALF_UP);
        }
        return null;
    }

    /** The campaign's forecast, or {@code null} when the per-review cost is unknown. */
    public @Nullable BigDecimal estimateTotalUsd(Long workspaceId, AgentJobType jobType, int artifacts) {
        BigDecimal perReview = meanCostPerReviewUsd(workspaceId, jobType);
        if (perReview == null) {
            return null;
        }
        return perReview.multiply(BigDecimal.valueOf(artifacts)).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal nullToZero(@Nullable BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
