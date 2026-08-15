package de.tum.cit.aet.hephaestus.agent.backfill;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Fleet-wide limits on review-backfill campaigns.
 *
 * @param batchSize how many artifacts one driver tick offers to the review path. Kept small because the
 *     usage ledger only learns a job's cost once it ends, and a budget cap is only checked between batches.
 * @param maxWindow the longest window a single campaign may cover.
 * @param maxArtifacts the largest scope a campaign may be confirmed for; refused at preflight.
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
