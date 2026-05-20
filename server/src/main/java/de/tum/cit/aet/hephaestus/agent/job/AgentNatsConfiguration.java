package de.tum.cit.aet.hephaestus.agent.job;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent NATS connection bean — always on when {@code hephaestus.agent.nats.enabled=true}.
 * Acquired by publishers (server-side {@code AgentJobSubmitter}) and consumers
 * (worker-side {@link AgentNatsConsumerConfig}); kept separate from consumer wiring so a
 * server-only deploy can still publish without spinning up the stream + pull consumer.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "hephaestus.agent.nats", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AgentNatsProperties.class)
public class AgentNatsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentNatsConfiguration.class);

    private final AgentNatsProperties properties;

    public AgentNatsConfiguration(AgentNatsProperties properties) {
        this.properties = properties;
    }

    @Bean(name = "agentNatsConnection", destroyMethod = "close")
    public Connection agentNatsConnection() throws IOException, InterruptedException {
        Options options = Options.builder()
            .server(properties.server())
            .connectionName("hephaestus-agent")
            .maxReconnects(-1)
            .reconnectWait(Duration.ofSeconds(2))
            .build();

        Connection connection = Nats.connect(options);
        log.info("Agent NATS connection established: server={}", properties.server());
        return connection;
    }
}
