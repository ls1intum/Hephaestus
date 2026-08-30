package de.tum.cit.aet.hephaestus.evidence.internal;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import de.tum.cit.aet.hephaestus.evidence.metrics.EvidenceMetrics;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
final class ArtifactSourceGovernanceMetrics {

    ArtifactSourceGovernanceMetrics(
            ArtifactSourceCatalogRegistry sourceCatalogs, Clock clock, MeterRegistry meterRegistry) {
        Gauge.builder(
                        EvidenceMetrics.ARTIFACT_SOURCE_GOVERNANCE_EXPIRY_SECONDS,
                        sourceCatalogs,
                        catalogs -> catalogs.earliestUseDecisionExpiry(null)
                                .map(expiry -> (double) Duration.between(clock.instant(), expiry)
                                        .toSeconds())
                                .orElse(Double.NaN))
                .description("Seconds until the earliest artifact-source use decision expires")
                .register(meterRegistry);
    }
}
