package de.tum.cit.aet.hephaestus.practices.observation.dto;

import java.time.Instant;

/**
 * Interface projection for per-practice aggregation of developer findings.
 *
 * <p>Used by the summary query that groups findings by practice and computes assessment
 * counts (ADR 0022). Spring Data JPA maps the query result columns to these getter methods.
 */
public interface DeveloperPracticeSummaryProjection {
    String getPracticeSlug();

    String getPracticeName();

    Long getTotalFindings();

    /** Count of present-or-absent observations the detector judged GOOD (a strength). */
    Long getGoodCount();

    /** Count of observations the detector judged BAD (a problem). */
    Long getBadCount();

    Instant getLastFindingAt();
}
