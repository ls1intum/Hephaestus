package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

/**
 * Agent NATS stream + durable pull consumer setup. Worker-only.
 *
 * <p>Stream bootstrap is idempotent ({@code updateStream} → {@code addStream} fallback);
 * config-mismatch races are logged at ERROR. Multi-replica workers should gate stream
 * creation via {@code hephaestus.agent.nats.bootstrap-stream=false} on replicas N+1 (not
 * needed today — worker is single-replica).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("${hephaestus.agent.nats.enabled:false} and ${" + RuntimeRole.WORKER_PROPERTY + ":true}")
public class AgentNatsConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentNatsConsumerConfig.class);
    private static final Duration MAX_STREAM_AGE = Duration.ofDays(7);

    private final AgentNatsProperties properties;
    private final Connection connection;

    public AgentNatsConsumerConfig(
        AgentNatsProperties properties,
        @Qualifier("agentNatsConnection") Connection connection
    ) {
        this.properties = properties;
        this.connection = connection;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1) // Must run before AgentJobExecutor.start() which uses @Order(2)
    public void ensureStreamAndConsumer() {
        if (connection == null) {
            return;
        }

        try {
            var jsm = connection.jetStreamManagement();

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
                        log.error(
                            "Failed to create AGENT stream — possible multi-replica race or config mismatch: {}",
                            addEx.getMessage(),
                            addEx
                        );
                        return;
                    }
                } else {
                    log.error("Failed to update AGENT stream: {}", e.getMessage(), e);
                    return;
                }
            }

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
