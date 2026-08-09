package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Audit snapshot of a campaign.
 *
 * <p>Carries the estimate as well as the scope on purpose: the question this trail has to answer is not
 * only "what did they authorise" but "what were they told it would cost when they authorised it". An
 * estimate that only lives on the confirmation screen is not evidence of anything afterwards.
 *
 * <p>And the discovery mode, because a nightly sweep and a hand-scoped campaign are the same row type
 * with the same estimate and very different accountability: one was authorised tonight, the other once,
 * months ago, by whoever created the schedule.
 */
record ReviewBackfillSnapshot(
    String artifactKind,
    DiscoveredVia discoveredVia,
    Instant fromAt,
    Instant toAt,
    ReviewBackfillStatus status,
    Integer estimatedArtifacts,
    @Nullable BigDecimal estimatedCostUsd
) implements ConfigAuditSnapshot {
    static ReviewBackfillSnapshot of(ReviewBackfillRun run) {
        return new ReviewBackfillSnapshot(
            run.getArtifactKind(),
            run.getDiscoveredVia(),
            run.getFromAt(),
            run.getToAt(),
            run.getStatus(),
            run.getEstimatedArtifacts(),
            run.getEstimatedCostUsd()
        );
    }
}
