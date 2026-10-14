package de.tum.cit.aet.hephaestus.integration.core.itemevent;

import de.tum.cit.aet.hephaestus.integration.core.metrics.IntegrationCoreMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.modulith.NamedInterface;
import org.springframework.stereotype.Component;

@Component
@NamedInterface(value = "item-events", propagate = false)
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
