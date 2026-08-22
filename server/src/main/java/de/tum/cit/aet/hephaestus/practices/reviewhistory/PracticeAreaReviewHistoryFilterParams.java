package de.tum.cit.aet.hephaestus.practices.reviewhistory;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RequestParam;

/** Query parameters for the learner-facing practice-area review history. */
public record PracticeAreaReviewHistoryFilterParams(
    @RequestParam(required = false) @Nullable String practiceSlug,
    @RequestParam(required = false) @Nullable List<ArtifactKind> artifactKinds,
    @RequestParam(required = false) @Nullable List<Severity> severities,
    @RequestParam(defaultValue = "0") @Min(0) int page,
    @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size
) {}
