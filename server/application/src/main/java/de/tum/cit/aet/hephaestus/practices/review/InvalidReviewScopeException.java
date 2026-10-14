package de.tum.cit.aet.hephaestus.practices.review;

import java.io.Serial;

public class InvalidReviewScopeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    InvalidReviewScopeException(String message) {
        super(message);
    }

    InvalidReviewScopeException(String message, Throwable cause) {
        super(message, cause);
    }
}
