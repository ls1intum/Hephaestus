package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.io.Serial;

public class ItemCaptureException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final ItemCaptureCode code;

    public ItemCaptureException(ItemCaptureCode code, String message) {
        super(message);
        this.code = code;
    }

    public ItemCaptureException(ItemCaptureCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ItemCaptureCode code() {
        return code;
    }
}
