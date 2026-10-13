package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.ObservationQueryFilter;
import de.tum.cit.aet.hephaestus.practices.result.ResultQueryFilter;
import de.tum.cit.aet.hephaestus.practices.web.QueryFilterSupport;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The results list's filters that mean something for observations. Only a not met result has observations, so there
 * is no outcome filter, and {@code severity} is the observation's own weight, not its result's.
 *
 * @param artifactKind a bare string for the reason {@link ReviewResultFilterParams} gives
 */
public record ReviewObservationFilterParams(
        @RequestParam(required = false) @Nullable List<String> practiceSlug,
        @RequestParam(required = false) @Nullable List<String> groupSlug,

        @Parameter(description = "Observation severities to list (repeatable)")
        @RequestParam(required = false)
        @Nullable
        List<Severity> severity,

        @Parameter(
                description = "true for only the observations of results an admin has marked incorrect and not"
                        + " restored, false for only the others; omit for both")
        @RequestParam(required = false)
        @Nullable
        Boolean invalidated,

        @Parameter(
                description = "true for only the observations of results the developer disputes feedback about,"
                        + " false for only the others; omit for both")
        @RequestParam(required = false)
        @Nullable
        Boolean disputed,

        @Parameter(description = "Only the observations of this result") @RequestParam(required = false) @Nullable
        UUID practiceResultId,

        @RequestParam(required = false) @Nullable UUID agentJobId,

        @Parameter(description = "Kind of reviewed work, e.g. scm.pull_request")
        @RequestParam(required = false)
        @Nullable
        String artifactKind,

        @Parameter(description = "Artifact ID; requires artifactKind")
        @RequestParam(required = false)
        @Positive
        @Nullable
        Long artifactId,

        @RequestParam(required = false) @Positive @Nullable Long subjectUserId,

        @Parameter(description = "What occasioned the measurement: LIVE, MANUAL or BACKFILL")
        @RequestParam(required = false)
        @Nullable
        List<ResultOrigin> origin,

        @Parameter(description = "Inclusive lower bound on when the result was recorded")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable
        Instant from,

        @Parameter(description = "Exclusive upper bound on when the result was recorded")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable
        Instant to) {

    /** Selects the results; outcome and severity stay open, since {@link #toObservationFilter()} owns severity. */
    public ResultQueryFilter toResultFilter() {
        ArtifactKind kind = QueryFilterSupport.artifactKind(artifactKind);
        QueryFilterSupport.requireKindForItem(kind, artifactId);
        QueryFilterSupport.requireWindow(from, to);
        return new ResultQueryFilter(
                practiceSlug,
                groupSlug,
                null,
                invalidated,
                disputed,
                null,
                agentJobId,
                kind,
                artifactId,
                subjectUserId,
                origin,
                from,
                to);
    }

    public ObservationQueryFilter toObservationFilter() {
        return new ObservationQueryFilter(severity, practiceResultId);
    }
}
