package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.LoggingUtils;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.net.URI;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@ConditionalOnServerRole
@RestControllerAdvice(basePackageClasses = ReviewScheduleController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReviewScheduleControllerAdvice {
    @ExceptionHandler(ReviewBatchConflictException.class)
    ProblemDetail batchConflict(ReviewBatchConflictException exception) {
        return conflict("review-batch-conflict", "Review batch conflict", exception.getMessage());
    }

    @ExceptionHandler(ReviewScheduleConflictException.class)
    ProblemDetail scheduleConflict(ReviewScheduleConflictException exception) {
        return conflict("review-schedule-conflict", "Review schedule conflict", exception.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentModification() {
        return conflict(
                "concurrent-modification",
                "Review configuration changed",
                "The review configuration changed. Try this request again.");
    }

    private static ProblemDetail conflict(String type, String title, @Nullable String message) {
        var detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setType(URI.create("/problems/" + type));
        detail.setTitle(title);
        detail.setDetail(Optional.ofNullable(message)
                .map(LoggingUtils::sanitizeForLog)
                .filter(text -> !text.isBlank())
                .orElse("This request conflicts with the current review configuration."));
        return detail;
    }
}
