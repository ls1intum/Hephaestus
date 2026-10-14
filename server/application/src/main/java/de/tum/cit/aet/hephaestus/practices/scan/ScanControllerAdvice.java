package de.tum.cit.aet.hephaestus.practices.scan;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@ConditionalOnServerRole
@RestControllerAdvice(basePackageClasses = ScanController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScanControllerAdvice {
    @ExceptionHandler(ScanRequestRejected.class)
    ProblemDetail rejected(ScanRequestRejected exception) {
        var detail = ProblemDetail.forStatusAndDetail(
                exception.reason() == ScanRequestRejected.Reason.REF_UNSUPPORTED
                        ? HttpStatus.UNPROCESSABLE_CONTENT
                        : HttpStatus.CONFLICT,
                exception.getMessage());
        switch (exception.reason()) {
            case REQUEST_CONFLICT -> detail.setProperty("scanId", exception.scanId());
            case SCAN_ACTIVE -> detail.setProperty("activeScanId", exception.scanId());
            case REF_UNSUPPORTED -> detail.setProperty("supportedRef", exception.value());
            case VERSION_CHANGED -> detail.setProperty("currentVersion", exception.value());
        }
        return detail;
    }
}
