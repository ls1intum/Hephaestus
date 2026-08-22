package de.tum.cit.aet.hephaestus.practices.observation.trend;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

/** One comparable practice opportunity, normalized across all findings in the latest run of one artifact. */
record EvidenceOpportunity(
    @NonNull String practiceSlug,
    @NonNull ArtifactKind artifactKind,
    long artifactId,
    @NonNull Instant occurredAt,
    @NonNull OutcomeVector outcomes,
    @NonNull TrendBundle bundle
) {
    boolean applicable() {
        return outcomes.applicable() > 0;
    }

    EvidenceOpportunity withBundle(TrendBundle value) {
        return new EvidenceOpportunity(practiceSlug, artifactKind, artifactId, occurredAt, outcomes, value);
    }
}
