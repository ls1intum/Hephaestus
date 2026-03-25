package de.tum.in.www1.hephaestus.practices.finding;

import de.tum.in.www1.hephaestus.practices.model.Verdict;
import java.time.Instant;

/**
 * Spring Data projection for aggregated practice finding summaries per contributor.
 *
 * <p>Each row represents one (practice, verdict) combination with the total count
 * and the most recent detection timestamp. Used by
 * {@link PracticeFindingRepository#findContributorPracticeSummary} to build
 * contributor history context for the review agent.
 */
public interface ContributorPracticeSummary {
    String getPracticeSlug();

    Verdict getVerdict();

    long getCount();

    Instant getLastDetectedAt();
}
