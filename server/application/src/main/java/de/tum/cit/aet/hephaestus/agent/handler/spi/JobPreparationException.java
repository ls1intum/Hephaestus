package de.tum.cit.aet.hephaestus.agent.handler.spi;

import java.io.Serial;

/** Preparation failures are terminal unless a classified subtype such as {@link ReviewSourceNotReadyException} permits retry. */
public class JobPreparationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Source-owned diagnostics only: these reasons neither authorize a retry nor disclose exception text. */
    public enum Reason {
        UNCLASSIFIED,
        REVIEWED_WORK_UNAVAILABLE,
        SOURCE_AUTHORITY_UNAVAILABLE,
        REVIEW_RANGE_UNAVAILABLE,
        INPUT_CONTRACT_INVALID,
        REQUIRED_SOURCE_COLLECTION_FAILED,
        WORKSPACE_RENDERING_FAILED,
    }

    private final Reason reason;

    public JobPreparationException(String message) {
        this(Reason.UNCLASSIFIED, message);
    }

    public JobPreparationException(String message, Throwable cause) {
        this(Reason.UNCLASSIFIED, message, cause);
    }

    public JobPreparationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public JobPreparationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
