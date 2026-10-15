package de.tum.cit.aet.hephaestus.integration.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.modulith.NamedInterface;

/** Keeps submission and execution refusals on the same operator-facing meter. */
@NamedInterface("review-metrics")
public final class ReviewRefusalRecorder {
    private ReviewRefusalRecorder() {}

    public static void record(MeterRegistry registry, String phase, String reason) {
        registry.counter(IntegrationCoreMetrics.PRACTICE_REVIEW_REFUSED, "phase", phase, "reason", reason)
                .increment();
    }
}
