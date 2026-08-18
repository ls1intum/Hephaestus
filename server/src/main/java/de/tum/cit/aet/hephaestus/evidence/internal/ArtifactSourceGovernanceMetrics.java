package de.tum.cit.aet.hephaestus.evidence.internal;

import de.tum.cit.aet.hephaestus.evidence.ArtifactSourceCatalogRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
final class ArtifactSourceGovernanceMetrics {

    ArtifactSourceGovernanceMetrics(
        ArtifactSourceCatalogRegistry sourceCatalogs,
        Clock clock,
        MeterRegistry meterRegistry
    ) {
        Gauge.builder("artifact.source.governance.expiry.seconds", sourceCatalogs, catalogs ->
            catalogs
                .earliestUseDecisionExpiry()
                .map(expiry -> (double) Duration.between(clock.instant(), expiry).toSeconds())
                .orElse(Double.NaN)
        )
            .description("Seconds until the earliest artifact-source use decision expires")
            .register(meterRegistry);
    }
}
