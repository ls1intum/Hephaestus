package de.tum.cit.aet.hephaestus.agent.scan;

import java.io.Serial;

public class ScanUsageUnverifiableException extends ScanModelUnavailableException {
    @Serial
    private static final long serialVersionUID = 1L;

    public ScanUsageUnverifiableException(String message) {
        super(message);
    }
}
