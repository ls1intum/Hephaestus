package de.tum.cit.aet.hephaestus.practices.result.dto;

import java.time.Instant;

/**
 * Interface projection for per-practice aggregation of developer results.
 *
 * <p>Used by the summary query that groups results by practice and computes assessment
 * counts (ADR 0022). Spring Data JPA maps the query result columns to these getter methods.
 */
public interface DeveloperPracticeSummaryProjection {
    String getPracticeSlug();

    String getPracticeName();

    Long getTotalResults();

    Long getMet();

    Long getNotMet();

    Long getNotApplicable();

    Long getUndetermined();

    Instant getLastObservedAt();
}
