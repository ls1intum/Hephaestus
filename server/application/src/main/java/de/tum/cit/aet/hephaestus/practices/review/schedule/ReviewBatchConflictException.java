package de.tum.cit.aet.hephaestus.practices.review.schedule;

import java.io.Serial;

/** The requested batch action conflicts with its current state. */
public class ReviewBatchConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewBatchConflictException(String message) {
        super(message);
    }
}
