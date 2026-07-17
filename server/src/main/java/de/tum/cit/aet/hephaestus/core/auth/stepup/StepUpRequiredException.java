package de.tum.cit.aet.hephaestus.core.auth.stepup;

import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A high-risk admin action was attempted without a recent-enough interactive authentication
 * ({@code auth_time} older than {@code hephaestus.auth.step-up-max-age}).
 *
 * <p>Maps to {@code 403 application/problem+json} with {@code code = step_up_required} +
 * {@code maxAgeSeconds}, per this API's ProblemDetail {@code code} convention. Deliberately not the
 * RFC 9470 challenge — see {@code docs/contributor/instance-admin.md} for why.
 */
public class StepUpRequiredException extends ErrorResponseException {

    /** Machine-readable ProblemDetail {@code code} the SPA switches on. */
    public static final String CODE = "step_up_required";

    public StepUpRequiredException(Duration maxAge) {
        super(HttpStatus.FORBIDDEN, problem(maxAge), null);
    }

    private static ProblemDetail problem(Duration maxAge) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN,
            "This action requires a recent sign-in. Confirm access by signing in again."
        );
        problem.setTitle("Confirm access");
        problem.setProperty("code", CODE);
        problem.setProperty("maxAgeSeconds", maxAge.toSeconds());
        return problem;
    }
}
