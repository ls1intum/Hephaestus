package de.tum.cit.aet.hephaestus.integration.core.webhook;

import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Reports {@code UP} only when the NATS connection is CONNECTED and all target streams
 * ({@code gitlab}, {@code github}, {@code slack}, {@code outline}) are reachable. Exception messages stay
 * in the log; only the exception class name is exposed in the health detail to avoid leaking NATS topology over an
 * actuator endpoint.
 */
public class WebhookHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(WebhookHealthIndicator.class);
    private static final String[] STREAMS = { "gitlab", "github", "slack", "outline" };

    private final Connection connection;
    private final JetStreamManagement jsm;

    WebhookHealthIndicator(Connection connection, JetStreamManagement jsm) {
        this.connection = connection;
        this.jsm = jsm;
    }

    @Override
    public Health health() {
        Connection.Status status = connection.getStatus();
        if (status != Connection.Status.CONNECTED) {
            return Health.down().withDetail("natsStatus", status.name()).build();
        }
        for (String streamName : STREAMS) {
            try {
                jsm.getStreamInfo(streamName);
            } catch (JetStreamApiException | IOException e) {
                log.warn("JetStream health probe failed: stream={}", streamName, e);
                return Health.down()
                    .withDetail("natsStatus", "CONNECTED")
                    .withDetail("stream." + streamName + ".error", e.getClass().getSimpleName())
                    .build();
            }
        }
        return Health.up().withDetail("natsStatus", "CONNECTED").build();
    }
}
