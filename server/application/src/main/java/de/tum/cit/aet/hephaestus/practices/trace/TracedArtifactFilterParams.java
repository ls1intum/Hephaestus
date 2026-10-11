package de.tum.cit.aet.hephaestus.practices.trace;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalledArtifactFilter;
import de.tum.cit.aet.hephaestus.practices.web.QueryFilterSupport;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The filters of the list of recorded work. {@code artifactKind} is a bare string, not an {@link ArtifactKind}, so
 * springdoc does not publish it as {@code artifactKind.value}; {@link #toFilter} parses it.
 */
public record TracedArtifactFilterParams(
        @Parameter(description = "Restrict to one kind of work, e.g. scm.pull_request")
        @RequestParam(required = false)
        @Nullable
        String artifactKind,

        @Parameter(description = "Restrict to one piece of work; requires artifactKind")
        @RequestParam(required = false)
        @Positive
        @Nullable
        Long artifactId,

        @Parameter(description = "Inclusive lower bound on when a signal occurred")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable
        Instant from,

        @Parameter(description = "Exclusive upper bound on when a signal occurred")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable
        Instant to,

        @Parameter(
                description = "Only the work none of whose signals in the window started a review, "
                        + "which is the work somebody expected a review on and did not get one")
        @RequestParam(required = false)
        @Nullable
        Boolean withoutReview) {

    /** The filter for one workspace; a backwards window, a blank or malformed kind or an item without its kind is a 400. */
    SignalledArtifactFilter toFilter(Long workspaceId) {
        QueryFilterSupport.requireWindow(from, to);
        ArtifactKind kind = QueryFilterSupport.artifactKind(artifactKind);
        QueryFilterSupport.requireKindForItem(kind, artifactId);
        return new SignalledArtifactFilter(
                workspaceId,
                kind == null ? null : kind.value(),
                artifactId,
                from,
                to,
                Boolean.TRUE.equals(withoutReview));
    }
}
