package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Audit snapshot of a campaign.
 *
 * <p>Carries the estimate as well as the scope on purpose: the question this trail has to answer is not
 * only "what did they authorise" but "what were they told it would cost when they authorised it". An
 * estimate that only lives on the confirmation screen is not evidence of anything afterwards.
 */
record ReviewBackfillSnapshot(
    String artifactKind,
    Instant fromAt,
    Instant toAt,
    ReviewBackfillStatus status,
    Integer estimatedArtifacts,
    @Nullable BigDecimal estimatedCostUsd
) implements ConfigAuditSnapshot {
    static ReviewBackfillSnapshot of(ReviewBackfillRun run) {
        return new ReviewBackfillSnapshot(
            run.getArtifactKind(),
            run.getFromAt(),
            run.getToAt(),
            run.getStatus(),
            run.getEstimatedArtifacts(),
            run.getEstimatedCostUsd()
        );
    }
}
