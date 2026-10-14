package de.tum.cit.aet.hephaestus.integration.core.handler;

import java.io.Serial;

public class PayloadParsingException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PayloadParsingException(String message, Throwable cause) {
        super(message, cause);
    }

    public PayloadParsingException(String message) {
        super(message);
    }
}
