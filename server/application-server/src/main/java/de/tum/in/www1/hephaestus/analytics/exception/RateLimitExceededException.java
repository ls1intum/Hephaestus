package de.tum.in.www1.hephaestus.analytics.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class RateLimitExceededException extends AnalyticsException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}