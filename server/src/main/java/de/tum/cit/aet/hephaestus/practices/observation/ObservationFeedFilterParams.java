package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Query parameters for the developer observation feed. Bound with {@code @ParameterObject}, so the wire
 * format stays one flat query string — this only changes the Java signature, not the HTTP contract.
 */
public record ObservationFeedFilterParams(
    @Parameter(description = "Filter by practice slug") @RequestParam(required = false) @Nullable String practiceSlug,
    @Parameter(description = "Filter by the practice area the observed practice belongs to")
    @RequestParam(required = false)
    @Nullable
    String areaSlug,
    @Parameter(description = "Filter by presence") @RequestParam(required = false) @Nullable Presence presence,
    @Parameter(description = "Only observations on these artifact kinds (repeatable); omit for all kinds")
    @RequestParam(required = false)
    @Nullable
    List<ArtifactKind> artifactKinds,
    @Parameter(description = "Only observations with these severities (repeatable); omit for all")
    @RequestParam(required = false)
    @Nullable
    List<Severity> severities,
    @Parameter(description = "Drop NOT_APPLICABLE rows — only observations where the practice actually applied")
    @RequestParam(required = false, defaultValue = "false")
    boolean displayableOnly,
    @Parameter(description = "Feed ordering: DATE (default) or SEVERITY (most severe first, ties newest-first)")
    @RequestParam(required = false, defaultValue = "DATE")
    ObservationService.ObservationSort sort,
    @Parameter(description = "Ordering direction: for DATE newest/oldest first, for SEVERITY most/least severe first")
    @RequestParam(required = false, defaultValue = "DESC")
    Sort.Direction direction,
    @RequestParam(defaultValue = "0") @Min(0) int page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
) {
    /** The domain-facing shape; {@code direction} collapses into the severity sort's only use of it. */
    public ObservationFeedQuery toQuery() {
        return new ObservationFeedQuery(
            practiceSlug,
            areaSlug,
            presence,
            artifactKinds,
            severities,
            displayableOnly,
            sort,
            direction == Sort.Direction.DESC
        );
    }
}
