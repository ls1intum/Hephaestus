package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequestRejected;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@ConditionalOnServerRole
@RestControllerAdvice(basePackages = "de.tum.cit.aet.hephaestus.practices")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReviewRequestControllerAdvice {
    @ExceptionHandler(ReviewRequestRejected.class)
    ProblemDetail rejected(ReviewRequestRejected exception) {
        var status =
                switch (exception.reason()) {
                    case REQUEST_CONFLICT, WORKSPACE_INACTIVE, SCAN_ACTIVE -> HttpStatus.CONFLICT;
                    case BUDGET_UNAVAILABLE -> HttpStatus.TOO_MANY_REQUESTS;
                    case ITEM_INELIGIBLE -> HttpStatus.UNPROCESSABLE_CONTENT;
                    case MODEL_UNAVAILABLE, SOURCE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
                };
        return ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    }
}
