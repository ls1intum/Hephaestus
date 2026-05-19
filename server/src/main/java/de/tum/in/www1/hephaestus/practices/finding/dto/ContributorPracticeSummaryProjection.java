package de.tum.in.www1.hephaestus.practices.finding.dto;

import java.time.Instant;

/**
 * Interface projection for per-practice aggregation of contributor findings.
 *
 * <p>Used by the summary JPQL query that groups findings by practice and computes
 * verdict counts. Spring Data JPA maps the query result columns to these getter methods.
 */
public interface ContributorPracticeSummaryProjection {
    String getPracticeSlug();

    String getPracticeName();

    String getCategory();

    Long getTotalFindings();

    Long getPositiveCount();

    Long getNegativeCount();

    Instant getLastFindingAt();
}
