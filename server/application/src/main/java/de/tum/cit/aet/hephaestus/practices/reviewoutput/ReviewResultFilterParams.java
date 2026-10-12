package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
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
 * @param artifactKind a bare string, not an {@link ArtifactKind} — {@link QueryFilterSupport#artifactKind} has the
 *     reason, and parses it in {@link #toFilter()}, where a malformed value becomes a 400
 * @param origin without it this surface cannot separate a campaign's results from live ones — a population-mixing
 *     hazard in exactly the place an operator judges whether a campaign was worth what it cost
 */
public record ReviewResultFilterParams(
        @RequestParam(required = false) @Nullable List<String> practiceSlug,
        @RequestParam(required = false) @Nullable List<String> groupSlug,

        @Parameter(description = "Review outcomes to list (repeatable)") @RequestParam(required = false) @Nullable
        List<Outcome> outcome,

        @Parameter(
                description = "true for only the results an admin has marked incorrect and not restored, false"
                        + " for only the others; omit for both")
        @RequestParam(required = false)
        @Nullable
        Boolean invalidated,

        @Parameter(
                description = "true for only the results the developer disputes feedback about, false for only"
                        + " the others; omit for both")
        @RequestParam(required = false)
        @Nullable
        Boolean disputed,

        @RequestParam(required = false) @Nullable List<Severity> severity,
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

        @Parameter(description = "Inclusive lower bound")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable
        Instant from,

        @Parameter(description = "Exclusive upper bound")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable
        Instant to) {
    public ResultQueryFilter toFilter() {
        ArtifactKind kind = QueryFilterSupport.artifactKind(artifactKind);
        QueryFilterSupport.requireKindForItem(kind, artifactId);
        QueryFilterSupport.requireWindow(from, to);
        return new ResultQueryFilter(
                practiceSlug,
                groupSlug,
                outcome,
                invalidated,
                disputed,
                severity,
                agentJobId,
                kind,
                artifactId,
                subjectUserId,
                origin,
                from,
                to);
    }
}
