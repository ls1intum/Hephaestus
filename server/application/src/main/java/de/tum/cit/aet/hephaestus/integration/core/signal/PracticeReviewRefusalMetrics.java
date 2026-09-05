package de.tum.cit.aet.hephaestus.integration.core.signal;

import de.tum.cit.aet.hephaestus.integration.core.metrics.IntegrationCoreMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Records submission and execution refusals without exposing the integration-core metric catalog. */
@Component
public class PracticeReviewRefusalMetrics {

    private final MeterRegistry registry;

    public PracticeReviewRefusalMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSubmissionRefusal(String reason) {
        recordRefusal("submission", reason);
    }

    public void recordExecutionRefusal(String reason) {
        recordRefusal("execution", reason);
    }

    private void recordRefusal(String phase, String reason) {
        registry.counter(IntegrationCoreMetrics.PRACTICE_REVIEW_REFUSED, "phase", phase, "reason", reason)
                .increment();
    }
}
