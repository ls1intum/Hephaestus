package de.tum.cit.aet.hephaestus.practices.review.schedule;

import java.io.Serial;

public class ReviewBatchConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewBatchConflictException(String message) {
        super(message);
    }
}
