package de.tum.cit.aet.hephaestus.practices.areadetail;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.web.QueryFilterSupport;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestParam;

public record PracticeAreaReviewRunFilterParams(
    @RequestParam(required = false) @Nullable String practiceSlug,
    @Parameter(description = "Only reviews of these artifact kinds, e.g. scm.pull_request (repeatable)")
    @RequestParam(required = false)
    @Nullable
    List<String> workKinds,
    @RequestParam(required = false) @Nullable List<Severity> severities,
    @Parameter(description = "Zero-based page") @RequestParam(required = false) @PositiveOrZero @Nullable Integer page,
    @Parameter(description = "Page size from 1 to 50")
    @RequestParam(required = false)
    @Min(1)
    @Max(50)
    @Nullable
    Integer size
) {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    public Pageable pageable() {
        return QueryFilterSupport.pageable(page, size, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
    }

    public @Nullable List<ArtifactKind> kinds() {
        return QueryFilterSupport.artifactKinds(workKinds);
    }
}
