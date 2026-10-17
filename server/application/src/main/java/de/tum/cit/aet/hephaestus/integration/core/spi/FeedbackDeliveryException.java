package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.io.Serial;

/** Delivery failed irrecoverably; provider adapters must not depend on runtime exception types. */
public class FeedbackDeliveryException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public FeedbackDeliveryException(String message) {
        super(message);
    }

    public FeedbackDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
