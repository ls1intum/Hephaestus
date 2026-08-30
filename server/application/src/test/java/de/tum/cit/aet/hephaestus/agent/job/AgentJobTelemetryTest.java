package de.tum.cit.aet.hephaestus.agent.job;

import static de.tum.cit.aet.hephaestus.testconfig.TestEntities.workspace;
import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class AgentJobTelemetryTest {

    @Test
    void shouldExposeOnlyBoundedLifecycleLabelsWhenRecordingTerminalJob() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new AgentJobTelemetry(registry);
        var job = new AgentJob();
        job.prePersist();
        job.setWorkspace(workspace(42L));
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);

        telemetry.terminal(job, AgentJobStatus.COMPLETED, Duration.ofSeconds(3));

        assertThat(registry.get("agent.job.total")
                        .tag("outcome", "completed")
                        .counter()
                        .count())
                .isEqualTo(1);
        var timer = registry.get("agent.job.duration").tag("phase", "total").timer();
        assertThat(timer.getId().getTags()).extracting(tag -> tag.getKey()).containsExactly("phase");
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(3);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTag("job.id")).isNull());
    }
}
