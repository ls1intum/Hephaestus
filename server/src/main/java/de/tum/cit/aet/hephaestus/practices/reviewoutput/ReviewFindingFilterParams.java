package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationQueryFilter;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

public record ReviewFindingFilterParams(
    @RequestParam(required = false) @Nullable List<String> practiceSlug,
    @RequestParam(required = false) @Nullable List<String> areaSlug,
    @RequestParam(required = false) @Nullable List<Presence> presence,
    @RequestParam(required = false) @Nullable List<Assessment> assessment,
    @RequestParam(required = false) @Nullable List<Severity> severity,
    @RequestParam(required = false) @Nullable UUID agentJobId,
    @RequestParam(required = false) @Nullable ArtifactKind artifactKind,
    @Parameter(description = "Artifact ID; requires artifactKind")
    @RequestParam(required = false)
    @Positive
    @Nullable
    Long artifactId,
    @RequestParam(required = false) @Positive @Nullable Long subjectUserId,
    @Parameter(description = "Inclusive lower bound")
    @RequestParam(required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Nullable
    Instant from,
    @Parameter(description = "Exclusive upper bound")
    @RequestParam(required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Nullable
    Instant to
) {
    public ObservationQueryFilter toFilter() {
        if (artifactId != null && artifactKind == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artifactId requires artifactKind");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        return new ObservationQueryFilter(
            practiceSlug,
            areaSlug,
            presence,
            assessment,
            severity,
            agentJobId,
            artifactKind,
            artifactId,
            subjectUserId,
            from,
            to
        );
    }
}
