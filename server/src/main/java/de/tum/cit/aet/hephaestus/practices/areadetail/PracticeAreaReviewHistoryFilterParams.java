package de.tum.cit.aet.hephaestus.practices.areadetail;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.web.QueryFilterSupport;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Query parameters for the developer-facing practice-area review history.
 *
 * <p>Every optional component is a nullable wrapper, and {@link QueryFilterSupport} states why, along
 * with how a raw artifact kind and a raw page become the values this surface reads.
 */
public record PracticeAreaReviewHistoryFilterParams(
    @RequestParam(required = false) @Nullable String practiceSlug,
    @Parameter(description = "Only reviews of these artifact kinds, e.g. scm.pull_request (repeatable)")
    @RequestParam(required = false)
    @Nullable
    List<String> workKinds,
    @RequestParam(required = false) @Nullable List<Severity> severities,
    @Parameter(description = "Zero-based page; a negative value is read as the first page")
    @RequestParam(required = false)
    @Nullable
    Integer page,
    @Parameter(description = "Page size, clamped to 1..50") @RequestParam(required = false) @Nullable Integer size
) {
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    /** The page to read, already normalised. */
    public Pageable pageable() {
        return QueryFilterSupport.pageable(page, size, DEFAULT_PAGE_SIZE, MAX_PAGE_SIZE);
    }

    /** The requested artifact kinds, parsed; {@code null} means "every kind". */
    public @Nullable List<ArtifactKind> kinds() {
        return QueryFilterSupport.artifactKinds(workKinds);
    }
}
