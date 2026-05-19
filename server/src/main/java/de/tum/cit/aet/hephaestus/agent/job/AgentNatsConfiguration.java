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
import org.springframework.context.annotation.Profile;

/**
 * Agent NATS connection bean — always-on whenever {@code hephaestus.agent.nats.enabled}
 * is true. Used by anyone who publishes to the agent subjects (server-side
 * {@code AgentJobSubmitter}, worker-side cancel handler, future zombie sweeper republish).
 *
 * <p>Split out from the original {@code AgentNatsConfiguration} so that publishers can
 * acquire the connection without dragging in the worker-only stream + consumer setup
 * (see {@link AgentNatsConsumerConfig}). Per ADR 0005 + pressure-test finding: gating
 * publishers behind the worker role would break server-side agent dispatch the moment
 * server and worker run as separate JVMs.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "hephaestus.agent.nats", name = "enabled", havingValue = "true")
@Profile("!specs")
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
