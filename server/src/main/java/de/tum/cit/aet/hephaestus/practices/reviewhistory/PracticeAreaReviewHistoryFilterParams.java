package de.tum.cit.aet.hephaestus.practices.reviewhistory;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Query parameters for the learner-facing practice-area review history.
 *
 * <p>Optional components are nullable wrappers defaulted in the compact constructor: a
 * {@code defaultValue} on {@code @RequestParam} does not reach a record bound as a
 * {@code @ParameterObject}, so a primitive component would receive {@code null} and answer 400 to a
 * request that named no filter at all.
 */
public record PracticeAreaReviewHistoryFilterParams(
    @RequestParam(required = false) @Nullable String practiceSlug,
    /**
     * Bare strings, not {@link ArtifactKind}s: springdoc publishes a typed parameter as
     * {@code artifactKinds.value}, so a generated client would send a query key no caller writes. The
     * grammar is enforced in {@link #kinds()}, where a malformed value becomes a 400.
     */
    @Parameter(description = "Only reviews of these artifact kinds, e.g. scm.pull_request (repeatable)")
    @RequestParam(required = false)
    @Nullable
    List<String> artifactKinds,
    @RequestParam(required = false) @Nullable List<Severity> severities,
    @Parameter(description = "Zero-based page; a negative value is read as the first page")
    @RequestParam(required = false)
    @Nullable
    Integer page,
    @Parameter(description = "Page size, clamped to 1..50") @RequestParam(required = false) @Nullable Integer size
) {
    public PracticeAreaReviewHistoryFilterParams {
        page = page == null || page < 0 ? 0 : page;
        size = size == null ? 10 : Math.clamp(size, 1, 50);
    }

    /** The requested artifact kinds, parsed; {@code null} means "every kind". */
    public @Nullable List<ArtifactKind> kinds() {
        if (artifactKinds == null) {
            return null;
        }
        try {
            return artifactKinds.stream().map(ArtifactKind::of).toList();
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalid.getMessage(), invalid);
        }
    }
}
