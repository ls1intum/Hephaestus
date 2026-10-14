package de.tum.cit.aet.hephaestus.practices.review.schedule;

import java.io.Serial;

/** The requested schedule conflicts with an existing schedule or a retained batch. */
public class ReviewScheduleConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewScheduleConflictException(String message) {
        super(message);
    }
}
