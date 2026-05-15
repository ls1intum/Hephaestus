package de.tum.in.www1.hephaestus.agent.sandbox.docker;

import de.tum.in.www1.hephaestus.agent.mentor.MentorAgentProperties;
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
public class MentorImagePullBootstrapper {

    private static final Logger log = LoggerFactory.getLogger(MentorImagePullBootstrapper.class);

    private final DockerImageOperations imageOps;
    private final MentorAgentProperties properties;
    private final MeterRegistry meterRegistry;

    public MentorImagePullBootstrapper(
        DockerImageOperations imageOps,
        MentorAgentProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.imageOps = imageOps;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void pullOnStartup() {
        ImagePullBootstrapperSupport.applyPolicy(
            properties.image(),
            properties.pullPolicy(),
            imageOps,
            "agent.mentor.image.pull",
            meterRegistry,
            log
        );
    }
}
