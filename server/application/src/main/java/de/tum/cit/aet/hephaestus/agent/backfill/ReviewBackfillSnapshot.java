package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Audit snapshot of a campaign.
 *
 * <p>Carries the estimate alongside the scope so the trail records not just what was authorised but what
 * it was estimated to cost when authorised, and the discovery mode because a nightly sweep and a
 * hand-scoped campaign share this row shape but carry different accountability.
 */
record ReviewBackfillSnapshot(
        String artifactKind,
        DiscoveredVia discoveredVia,
        Instant fromAt,
        Instant toAt,
        ReviewBackfillStatus status,
        Integer estimatedArtifacts,
        @Nullable BigDecimal estimatedCostUsd)
        implements ConfigAuditSnapshot {
    static ReviewBackfillSnapshot of(ReviewBackfillRun run) {
        return new ReviewBackfillSnapshot(
                run.getArtifactKind(),
                run.getDiscoveredVia(),
                run.getFromAt(),
                run.getToAt(),
                run.getStatus(),
                run.getEstimatedArtifacts(),
                run.getEstimatedCostUsd());
    }
}
