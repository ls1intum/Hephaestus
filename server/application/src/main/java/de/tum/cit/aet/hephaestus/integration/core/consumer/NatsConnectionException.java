package de.tum.cit.aet.hephaestus.integration.core.consumer;

import java.io.Serial;

/**
 * Thrown when the integration framework's NATS connection cannot be established or has
 * been lost beyond the orchestrator's reconnect budget.
 *
 * <p>Distinct from the JetStream client's {@code JetStreamApiException} so callers can
 * tell "no transport" from "transport up, request failed". Carries no payload: the
 * connection-status label exposed on {@code IntegrationConsumerStats} is the single
 * source of truth for what went wrong.
 */
public class NatsConnectionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NatsConnectionException(String message) {
        super(message);
    }

    public NatsConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
