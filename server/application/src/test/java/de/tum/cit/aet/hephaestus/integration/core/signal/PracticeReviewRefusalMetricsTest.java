package de.tum.cit.aet.hephaestus.integration.core.signal;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PracticeReviewRefusalMetricsTest {

    @Test
    void shouldPreserveCounterIdentityAndCountsAcrossRefusalPhases() {
        var registry = new SimpleMeterRegistry();
        try {
            var metrics = new PracticeReviewRefusalMetrics(registry);
            metrics.recordSubmissionRefusal("budget_exhausted");
            metrics.recordExecutionRefusal("budget_exhausted");
            metrics.recordExecutionRefusal("budget_exhausted");

            assertThat(registry.getMeters()).hasSize(2).allSatisfy(meter -> {
                assertThat(meter.getId().getName()).isEqualTo("practice.review.refused");
                assertThat(meter.getId().getType()).isEqualTo(Meter.Type.COUNTER);
                assertThat(meter.getId().getTags()).hasSize(2);
                assertThat(meter.getId().getTag("reason")).isEqualTo("budget_exhausted");
            });
            assertThat(registry.get("practice.review.refused")
                            .tag("phase", "submission")
                            .counter()
                            .count())
                    .isEqualTo(1);
            assertThat(registry.get("practice.review.refused")
                            .tag("phase", "execution")
                            .counter()
                            .count())
                    .isEqualTo(2);
        } finally {
            registry.close();
        }
    }
}
