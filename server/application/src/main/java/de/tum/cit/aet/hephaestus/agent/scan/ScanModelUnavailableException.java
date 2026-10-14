package de.tum.cit.aet.hephaestus.agent.scan;

import java.io.Serial;

/** A frozen model cannot provide a finite, verifiable scan budget. */
public class ScanModelUnavailableException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ScanModelUnavailableException(String message) {
        super(message);
    }
}
