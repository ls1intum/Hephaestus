package de.tum.cit.aet.hephaestus.practices.spi;

/** A request stopped before any review rows were written. */
public final class ReviewRequestRejected extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        REQUEST_CONFLICT,
        WORKSPACE_INACTIVE,
        SCAN_ACTIVE,
        BUDGET_UNAVAILABLE,
        ITEM_INELIGIBLE,
        MODEL_UNAVAILABLE,
        SOURCE_UNAVAILABLE
    }

    private final Reason reason;

    public ReviewRequestRejected(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
