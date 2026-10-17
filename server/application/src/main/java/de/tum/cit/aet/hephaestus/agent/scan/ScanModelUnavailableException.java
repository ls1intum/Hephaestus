package de.tum.cit.aet.hephaestus.agent.scan;

import java.io.Serial;

public class ScanModelUnavailableException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ScanModelUnavailableException(String message) {
        super(message);
    }

    public ScanModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
