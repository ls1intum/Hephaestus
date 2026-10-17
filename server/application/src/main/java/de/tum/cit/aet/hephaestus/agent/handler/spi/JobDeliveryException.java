package de.tum.cit.aet.hephaestus.agent.handler.spi;

import java.io.Serial;

public class JobDeliveryException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public JobDeliveryException(String message) {
        super(message);
    }

    public JobDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
