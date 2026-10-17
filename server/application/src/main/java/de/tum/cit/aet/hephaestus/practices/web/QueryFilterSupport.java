package de.tum.cit.aet.hephaestus.practices.web;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Shared parsing for practice query parameters. */
public final class QueryFilterSupport {

    private QueryFilterSupport() {}

    /** Rejects a backwards window, which would otherwise answer an empty page for no visible reason. */
    public static void requireWindow(@Nullable Instant from, @Nullable Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "The start of the range must not be after its end.");
        }
    }

    /** Builds an unsorted page after the parameter record has validated supplied bounds. */
    public static Pageable pageable(@Nullable Integer page, @Nullable Integer size, int defaultSize) {
        return PageRequest.of(page == null ? 0 : page, size == null ? defaultSize : size);
    }
}
