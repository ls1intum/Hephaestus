package de.tum.cit.aet.hephaestus.practices.review.schedule;

import java.io.Serial;

public class ReviewScheduleConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ReviewScheduleConflictException(String message) {
        super(message);
    }
}
