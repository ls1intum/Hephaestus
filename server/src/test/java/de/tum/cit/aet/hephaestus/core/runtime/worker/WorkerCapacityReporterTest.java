package de.tum.cit.aet.hephaestus.core.runtime.worker;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.CapacityReport;
import de.tum.cit.aet.hephaestus.core.runtime.worker.protocol.WorkerControlFrame;
import de.tum.cit.aet.hephaestus.core.runtime.worker.testing.CapturingPublisher;
import de.tum.cit.aet.hephaestus.core.runtime.worker.testing.WorkerPropertiesFixtures;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkerCapacityReporterTest extends BaseUnitTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final CapturingPublisher publisher = new CapturingPublisher();

    @AfterEach
    void shutdown() {
        scheduler.shutdownNow();
    }

    @Test
    void emitsCapacityReportSnapshot() {
        WorkerProperties props = WorkerPropertiesFixtures.minimal("3", "2");
        WorkerCapacityState state = new WorkerCapacityState(props);
        state.claimReview();
        WorkerCapacityReporter reporter = new WorkerCapacityReporter(state, publisher, props, scheduler, registry);

        reporter.tick();

        assertThat(publisher.sent).hasSize(1);
        CapacityReport report = (CapacityReport) publisher.sent.get(0);
        assertThat(report.reviewMax()).isEqualTo(3);
        assertThat(report.mentorMax()).isEqualTo(2);
        assertThat(report.inFlightReview()).isEqualTo(1);
        assertThat(report.spareReview()).isEqualTo(2);
        assertThat(registry.counter("worker.heartbeats.sent").count()).isEqualTo(1.0);
    }

    @Test
    void incrementsFailedCounterOnPublisherException() {
        WorkerProperties props = WorkerPropertiesFixtures.minimal("1", "1");
        WorkerCapacityState state = new WorkerCapacityState(props);
        WorkerControlPublisher throwing = new WorkerControlPublisher() {
            @Override
            public void send(WorkerControlFrame frame) {
                throw new RuntimeException("boom");
            }

            @Override
            public boolean isConnected() {
                return false;
            }

            @Override
            public Instant lastInboundAt() {
                return Instant.EPOCH;
            }
        };
        WorkerCapacityReporter reporter = new WorkerCapacityReporter(state, throwing, props, scheduler, registry);

        reporter.tick();

        // A failed tick must NOT propagate — the scheduler thread would die.
        assertThat(registry.counter("worker.heartbeats.failed").count()).isEqualTo(1.0);
        assertThat(registry.counter("worker.heartbeats.sent").count()).isZero();
    }
}
