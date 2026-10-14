package de.tum.cit.aet.hephaestus.practices.spi;

import org.jspecify.annotations.Nullable;

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
        SOURCE_UNAVAILABLE;

        public boolean transientReason() {
            return this == BUDGET_UNAVAILABLE
                    || this == MODEL_UNAVAILABLE
                    || this == SOURCE_UNAVAILABLE
                    || this == SCAN_ACTIVE;
        }
    }

    private final Reason reason;
    private final @Nullable ReviewRequestReason eventReason;

    public ReviewRequestRejected(Reason reason, String message) {
        this(reason, message, null);
    }

    public ReviewRequestRejected(Reason reason, String message, @Nullable ReviewRequestReason eventReason) {
        super(message);
        this.eventReason = eventReason;
        this.reason = reason;
    }

    public @Nullable ReviewRequestReason eventReason() {
        return eventReason;
    }

    public Reason reason() {
        return reason;
    }
}
