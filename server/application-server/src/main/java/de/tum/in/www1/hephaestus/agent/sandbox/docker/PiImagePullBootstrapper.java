package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import de.tum.in.www1.hephaestus.agent.runtime.PiAgentProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "hephaestus.sandbox", name = "enabled", havingValue = "true")
public class PiImagePullBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(PiImagePullBootstrapper.class);

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
    @Order(0) // before AgentJobExecutor (Order 2)
    public void pullOnStartup() {
        ImagePullBootstrapperSupport.applyPolicy(
            properties.image(),
            properties.pullPolicy(),
            imageOps,
            "agent.pi.image.pull",
            meterRegistry,
            log
        );
    }
}
