package de.tum.cit.aet.hephaestus.agent.backfill;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Fleet-wide limits on review-backfill campaigns.
 *
 * @param batchSize how many artifacts one driver tick offers to the review path. Small on purpose: it
 *     bounds how far a campaign can overshoot a budget cap it crosses mid-batch, because the cap is
 *     checked between batches and the usage ledger only learns of a job's cost when the job ends.
 * @param maxWindow the longest window a single campaign may cover. A guard rail on the estimate rather
 *     than on the spend — an admin asking to review five years of history is far more likely to have
 *     mistyped a date than to mean it.
 * @param maxArtifacts the largest scope a campaign may be confirmed for. Refused at preflight, with the
 *     count in the message, so the admin narrows the window instead of discovering the limit later.
 * @param costHistoryWindow how far back the estimator looks for completed reviews to derive a per-review
 *     cost from. Long enough to survive a quiet fortnight, short enough that a model change moves it.
 */
@ConfigurationProperties(prefix = "hephaestus.practice-review.backfill")
public record ReviewBackfillProperties(
    @DefaultValue("25") int batchSize,
    @DefaultValue("400d") Duration maxWindow,
    @DefaultValue("5000") int maxArtifacts,
    @DefaultValue("90d") Duration costHistoryWindow
) {
    public ReviewBackfillProperties {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive, got " + batchSize);
        }
        if (maxArtifacts <= 0) {
            throw new IllegalArgumentException("maxArtifacts must be positive, got " + maxArtifacts);
        }
    }
}
