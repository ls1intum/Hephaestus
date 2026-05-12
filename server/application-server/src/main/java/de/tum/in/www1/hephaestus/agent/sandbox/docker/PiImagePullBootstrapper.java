package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import de.tum.in.www1.hephaestus.agent.runtime.PiAgentProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Pulls the configured Pi image into the local Docker daemon cache once the application is
 * ready. Closes the rolling-deploy hole where a stale {@code :latest} image reads the legacy
 * workspace ABI while the new server writes the current one — {@code createContainer}
 * otherwise never re-pulls.
 *
 * <p>Runs at {@link Order} 0 — <b>before</b> the agent job executor's listener so the first
 * job processed sees a fresh image. The pull is bounded to 5 minutes inside
 * {@link DockerClientOperations#pullImage} and failure is logged but does not block startup
 * (the daemon's cached image is a fallback; the runner's exit-{@code 42} envelope check is
 * the secondary safety net).
 *
 * <p>Opt in via {@code hephaestus.agent.pi.pull-on-startup=true}.
 */
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent.pi", name = "pull-on-startup", havingValue = "true")
public class PiImagePullBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(PiImagePullBootstrapper.class);
    private static final String METRIC_PULL = "agent.pi.image.pull";

    private final DockerImageOperations imageOps;
    private final PiAgentProperties properties;
    private final MeterRegistry meterRegistry;

    public PiImagePullBootstrapper(
        DockerImageOperations imageOps,
        PiAgentProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.imageOps = imageOps;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(0) // Before AgentJobExecutor's listener (Order 2) so the first job sees a fresh image.
    public void pullOnStartup() {
        if (!imageOps.ping()) {
            log.warn("Docker daemon not reachable; skipping startup image pull for {}", properties.image());
            meterRegistry.counter(METRIC_PULL + ".skipped", "reason", "docker_unreachable").increment();
            return;
        }
        Timer.Sample sample = Timer.start(meterRegistry);
        boolean ok = imageOps.pullImage(properties.image());
        sample.stop(meterRegistry.timer(METRIC_PULL + ".duration", "outcome", ok ? "success" : "failure"));
        if (!ok) {
            log.warn(
                "Startup pull of {} did not complete; daemon's cached image will be used. " +
                    "Runner exit 42 will fire on envelope-version mismatch.",
                properties.image()
            );
            meterRegistry.counter(METRIC_PULL + ".failure").increment();
        }
    }
}
