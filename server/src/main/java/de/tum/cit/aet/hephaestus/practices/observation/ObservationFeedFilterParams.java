package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Query parameters for the developer observation feed. Bound with {@code @ParameterObject}, so the wire
 * format stays one flat query string — this only changes the Java signature, not the HTTP contract.
 *
 * <p>Every optional component is a nullable wrapper, and the compact constructor supplies the defaults.
 * A {@code defaultValue} on {@code @RequestParam} does not reach a record bound this way: the binder
 * constructs the record and hands a primitive component {@code null}, which fails conversion and answers
 * 400 to a request that named no filter at all.
 */
public record ObservationFeedFilterParams(
    @Parameter(description = "Filter by practice slug") @RequestParam(required = false) @Nullable String practiceSlug,
    @Parameter(description = "Filter by the practice area the observed practice belongs to")
    @RequestParam(required = false)
    @Nullable
    String areaSlug,
    @Parameter(description = "Filter by presence") @RequestParam(required = false) @Nullable Presence presence,
    /**
     * Bare strings, not {@link ArtifactKind}s: springdoc walks into the record and publishes a typed
     * parameter as {@code artifactKinds.value}, so a generated client would send a query key no caller
     * writes. The grammar is enforced in {@link #toQuery()} instead, where a malformed value becomes a 400.
     */
    @Parameter(description = "Only observations on these artifact kinds, e.g. scm.pull_request (repeatable)")
    @RequestParam(required = false)
    @Nullable
    List<String> artifactKinds,
    @Parameter(description = "Only observations with these severities (repeatable); omit for all")
    @RequestParam(required = false)
    @Nullable
    List<Severity> severities,
    @Parameter(description = "Drop NOT_APPLICABLE rows — only observations where the practice actually applied")
    @RequestParam(required = false)
    @Nullable
    Boolean displayableOnly,
    @Parameter(description = "Feed ordering: DATE (default) or SEVERITY (most severe first, ties newest-first)")
    @RequestParam(required = false)
    ObservationService.@Nullable ObservationSort sort,
    @Parameter(description = "Ordering direction: for DATE newest/oldest first, for SEVERITY most/least severe first")
    @RequestParam(required = false)
    Sort.@Nullable Direction direction,
    @Parameter(description = "Zero-based page; a negative value is read as the first page")
    @RequestParam(required = false)
    @Nullable
    Integer page,
    @Parameter(description = "Page size, clamped to 1..100") @RequestParam(required = false) @Nullable Integer size
) {
    public ObservationFeedFilterParams {
        displayableOnly = displayableOnly != null && displayableOnly;
        sort = sort == null ? ObservationService.ObservationSort.DATE : sort;
        direction = direction == null ? Sort.Direction.DESC : direction;
        // Clamped, not rejected: a feed is a reading surface, and answering 400 to "?size=999" tells a
        // reader nothing they can act on while hiding data they are allowed to see.
        page = page == null || page < 0 ? 0 : page;
        size = size == null ? 20 : Math.clamp(size, 1, 100);
    }

    /**
     * The page to read, already normalised and sorted.
     *
     * <p>The compact constructor above defaults every paging component, so none is null by the time
     * anything reads it. The severity query carries its own ORDER BY, so it must not also receive a sort:
     * adding one would have the database order by two different keys.
     */
    public Pageable pageable() {
        PageRequest page = PageRequest.of(Objects.requireNonNull(this.page), Objects.requireNonNull(size));
        return sort() == ObservationService.ObservationSort.SEVERITY
            ? page
            : PageRequest.of(
                  page.getPageNumber(),
                  page.getPageSize(),
                  Sort.by(Objects.requireNonNull(direction), "observedAt")
              );
    }

    /** The domain-facing shape; {@code direction} collapses into the severity sort's only use of it. */
    public ObservationFeedQuery toQuery() {
        return new ObservationFeedQuery(
            practiceSlug,
            areaSlug,
            presence,
            parseArtifactKinds(),
            severities,
            Objects.requireNonNull(displayableOnly),
            Objects.requireNonNull(sort),
            direction == Sort.Direction.DESC
        );
    }

    private @Nullable List<ArtifactKind> parseArtifactKinds() {
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
