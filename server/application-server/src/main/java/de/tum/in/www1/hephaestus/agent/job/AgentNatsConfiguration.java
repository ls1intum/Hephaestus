package de.tum.in.www1.hephaestus.agent.job;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

/**
 * NATS JetStream configuration for the agent job execution pipeline.
 *
 * <p>Creates the AGENT stream (WorkQueue retention) and a durable pull consumer. The connection
 * is self-managed — independent of the sync module's NATS connection so either can be
 * enabled/disabled independently.
 */
@Configuration
@ConditionalOnProperty(prefix = "hephaestus.agent.nats", name = "enabled", havingValue = "true")
@Profile("!specs")
@EnableConfigurationProperties(AgentNatsProperties.class)
public class AgentNatsConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AgentNatsConfiguration.class);

    private static final Duration MAX_STREAM_AGE = Duration.ofDays(7);

    private final AgentNatsProperties properties;
    private Connection connection;

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

        this.connection = Nats.connect(options);
        log.info("Agent NATS connection established: server={}", properties.server());
        return this.connection;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1) // Must run before AgentJobExecutor.start() which uses @Order(2)
    public void ensureStreamAndConsumer() {
        if (connection == null) {
            return;
        }

        try {
            var jsm = connection.jetStreamManagement();

            // Create or update AGENT stream
            StreamConfiguration streamConfig = StreamConfiguration.builder()
                .name(properties.streamName())
                .subjects(AgentNatsProperties.SUBJECT_WILDCARD)
                .retentionPolicy(RetentionPolicy.WorkQueue)
                .storageType(StorageType.File)
                .maxAge(MAX_STREAM_AGE)
                .build();

            try {
                jsm.updateStream(streamConfig);
                log.info("Updated AGENT stream: name={}", properties.streamName());
            } catch (JetStreamApiException e) {
                if (e.getErrorCode() == 404) {
                    try {
                        jsm.addStream(streamConfig);
                        log.info("Created AGENT stream: name={}", properties.streamName());
                    } catch (JetStreamApiException addEx) {
                        log.error("Failed to create AGENT stream: {}", addEx.getMessage(), addEx);
                        return;
                    }
                } else {
                    log.error("Failed to update AGENT stream: {}", e.getMessage(), e);
                    return;
                }
            }

            // Create or update durable pull consumer
            ConsumerConfiguration consumerConfig = ConsumerConfiguration.builder()
                .durable(properties.consumerName())
                .filterSubject(AgentNatsProperties.SUBJECT_WILDCARD)
                .deliverPolicy(DeliverPolicy.All)
                .ackWait(properties.ackWait())
                .maxDeliver(properties.maxDeliver())
                .maxAckPending(properties.maxAckPending())
                .build();

            try {
                jsm.addOrUpdateConsumer(properties.streamName(), consumerConfig);
                log.info(
                    "Configured consumer: name={}, ackWait={}, maxDeliver={}, maxAckPending={}",
                    properties.consumerName(),
                    properties.ackWait(),
                    properties.maxDeliver(),
                    properties.maxAckPending()
                );
            } catch (JetStreamApiException e) {
                log.error("Failed to configure NATS consumer: {}", e.getMessage(), e);
            }
        } catch (IOException e) {
            log.error("Failed to initialize agent NATS stream/consumer: {}", e.getMessage(), e);
        }
    }
}
