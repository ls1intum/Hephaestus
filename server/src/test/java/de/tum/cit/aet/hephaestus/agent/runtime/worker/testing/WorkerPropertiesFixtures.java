package de.tum.cit.aet.hephaestus.agent.runtime.worker.testing;

import de.tum.cit.aet.hephaestus.agent.runtime.worker.WorkerProperties;
import java.net.URI;
import java.time.Duration;

public final class WorkerPropertiesFixtures {

    private WorkerPropertiesFixtures() {}

    /** Minimal {@link WorkerProperties} for unit-test wiring with the given capacity. */
    public static WorkerProperties minimal(String reviewMax, String mentorMax) {
        return new WorkerProperties(
            "test-worker",
            new WorkerProperties.Capacity(reviewMax, mentorMax),
            new WorkerProperties.Drain(Duration.ofMinutes(5)),
            new WorkerProperties.Heartbeat(Duration.ofSeconds(20)),
            new WorkerProperties.Control(URI.create("ws://example"), "tok", Duration.ofSeconds(10)),
            new WorkerProperties.Llm(null, null)
        );
    }

    public static WorkerProperties withDrain(Duration drainTimeout) {
        return new WorkerProperties(
            "w",
            new WorkerProperties.Capacity("2", "1"),
            new WorkerProperties.Drain(drainTimeout),
            new WorkerProperties.Heartbeat(Duration.ofSeconds(20)),
            new WorkerProperties.Control(URI.create("ws://example"), "tok", Duration.ofSeconds(10)),
            new WorkerProperties.Llm(null, null)
        );
    }
}
