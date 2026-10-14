package de.tum.cit.aet.hephaestus.practices.review.schedule;

import java.io.Serial;

/** A campaign cannot be started, cancelled or superseded from the state it is in. Maps to 409. */
public class ReviewBatchConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewBatchConflictException(String message) {
        super(message);
    }
}
