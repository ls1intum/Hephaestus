package de.tum.cit.aet.hephaestus.practices.finding;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import java.time.Instant;

/**
 * Spring Data projection for aggregated practice finding summaries per developer.
 *
 * <p>Each row represents one (practice, presence, assessment) combination with the total count
 * and the most recent detection timestamp. Used by
 * {@link PracticeFindingRepository#findDeveloperPracticeSummary} to build
 * developer history context for the review agent.
 */
public interface DeveloperPracticeSummary {
    String getPracticeSlug();

    Presence getPresence();

    Assessment getAssessment();

    long getCount();

    Instant getLastDetectedAt();
}
