package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequestReason;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** A request stopped before any review rows were written. */
public final class ReviewRequestRejected extends ErrorResponseException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        REQUEST_CONFLICT,
        REF_UNSUPPORTED,
        VERSION_CHANGED,
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
        super(status(reason), ProblemDetail.forStatusAndDetail(status(reason), message), null);
        this.eventReason = eventReason;
        this.reason = reason;
    }

    private static HttpStatus status(Reason reason) {
        return switch (reason) {
            case REQUEST_CONFLICT, VERSION_CHANGED, WORKSPACE_INACTIVE, SCAN_ACTIVE -> HttpStatus.CONFLICT;
            case BUDGET_UNAVAILABLE -> HttpStatus.TOO_MANY_REQUESTS;
            case ITEM_INELIGIBLE, REF_UNSUPPORTED -> HttpStatus.UNPROCESSABLE_CONTENT;
            case MODEL_UNAVAILABLE, SOURCE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }

    public @Nullable ReviewRequestReason eventReason() {
        return eventReason;
    }

    public Reason reason() {
        return reason;
    }
}
