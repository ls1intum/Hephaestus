package de.tum.cit.aet.hephaestus.agent.job;

import io.swagger.v3.oas.annotations.Parameter;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Optional filters for the review listing, flattened onto the query string by {@link ParameterObject}.
 *
 * <p>The row shows when a review was requested, so the list can be narrowed to a window of requests:
 * every piece of information a list item displays is important enough that someone will look for a
 * way to filter by it, and finding none they search the toolbar repeatedly rather than concluding it
 * is absent (Baymard, "Have Filters for All Displayed List Item Info").
 *
 * <p>{@code from}/{@code to} are the same half-open window the sibling observation and feedback
 * listings take — see {@code ReviewObservationFilterParams} — down to the rejection message, so the
 * three surfaces under {@code /practices/reviews} answer a pasted date range identically.
 */
public record ReviewRunFilterParams(
    @RequestParam(required = false) @Nullable AgentJobStatus status,
    @Parameter(description = "Inclusive lower bound on when the review was requested")
    @RequestParam(required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Nullable
    Instant from,
    @Parameter(description = "Exclusive upper bound on when the review was requested")
    @RequestParam(required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Nullable
    Instant to
) {
    /** Rejects a backwards window, which would otherwise return an empty page for no visible reason. */
    public ReviewRunFilterParams validated() {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        return this;
    }
}
