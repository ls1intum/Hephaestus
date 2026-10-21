package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.io.Serial;

/** Why a {@link ContextLoader} could not load an item. The message is for the model to read. */
public class ContextLoadException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public enum Code {
        /** Unknown, unmonitored, hidden, or of another workspace: one text for all, so none can be told apart. */
        NOT_VISIBLE,
        REF_NOT_FOUND,
        UNAVAILABLE
    }

    private final Code code;

    public ContextLoadException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ContextLoadException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
