package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import de.tum.in.www1.hephaestus.agent.runtime.AgentImageProperties;
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
public class AgentImagePullBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(AgentImagePullBootstrapper.class);

    private final DockerImageOperations imageOps;
    private final AgentImageProperties properties;
    private final MeterRegistry meterRegistry;

    public AgentImagePullBootstrapper(
        DockerImageOperations imageOps,
        AgentImageProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.imageOps = imageOps;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(0) // run before any agent-container launcher
    public void pullOnStartup() {
        ImagePullBootstrapperSupport.applyPolicy(
            properties.reference(),
            properties.pullPolicy(),
            imageOps,
            "agent.image.pull",
            meterRegistry,
            log
        );
    }
}
