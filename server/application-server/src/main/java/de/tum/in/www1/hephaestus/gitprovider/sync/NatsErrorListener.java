package de.tum.in.www1.hephaestus.gitprovider.sync;

import io.nats.client.Connection;
import io.nats.client.Consumer;
import io.nats.client.ErrorListener;
import io.nats.client.JetStreamSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Error listener for NATS JetStream operations.
 * <p>
 * Provides structured logging for NATS errors and heartbeat alarms
 * to aid in debugging connection issues. Uses structured log format
 * compatible with JSON log aggregators.
 */
public class NatsErrorListener implements ErrorListener {

    private static final Logger logger = LoggerFactory.getLogger(NatsErrorListener.class);

    @Override
    public void errorOccurred(Connection conn, String error) {
        logger.error("nats.error serverPort={} message={}", getServerPort(conn), error);
    }

    @Override
    public void heartbeatAlarm(
        Connection conn,
        JetStreamSubscription sub,
        long lastStreamSequence,
        long lastConsumerSequence
    ) {
        String consumerName = sub != null ? sub.getConsumerName() : "unknown";
        logger.warn(
            "nats.heartbeat_alarm consumer={} streamSeq={} consumerSeq={}",
            consumerName,
            lastStreamSequence,
            lastConsumerSequence
        );
    }

    @Override
    public void slowConsumerDetected(Connection conn, Consumer consumer) {
        String consumerName = consumer != null ? consumer.toString() : "unknown";
        logger.warn("nats.slow_consumer_detected consumer={}", consumerName);
    }

    private String getServerPort(Connection conn) {
        if (conn != null && conn.getServerInfo() != null) {
            return String.valueOf(conn.getServerInfo().getPort());
        }
        return "unknown";
    }
}
