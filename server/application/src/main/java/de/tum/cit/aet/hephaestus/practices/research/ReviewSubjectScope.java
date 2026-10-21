package de.tum.cit.aet.hephaestus.practices.research;

import de.tum.cit.aet.hephaestus.practices.research.ResearchSnapshot.ReviewSubjectRow;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Whether a whole review is about authorised participants only. Whole-review facts that are not
 * about one result — model usage, assignment counts and listing sizes — are exported only for
 * {@link #ALL_SUBJECTS_PARTICIPATING}. Any other value is the typed reason they are withheld. Usage
 * is never split or allocated to a person.
 */
enum ReviewSubjectScope {
    /**
     * Every recorded assignment names its subject: by its result, or by the developer an unjudged ITEM
     * assignment recorded. Every subject so named, in or out of the window, is an authorised participant.
     */
    ALL_SUBJECTS_PARTICIPATING,
    /** A result or recorded assignment developer of this review is an account without a current grant. */
    INCLUDES_NON_PARTICIPANT,
    /**
     * An assignment has neither a result nor a usable recorded developer, or a result has no developer,
     * so who it is about is not known.
     */
    ATTRIBUTION_UNKNOWN,
    /** The review has no recorded assignments, as migrated reviews do; its population is unknown. */
    ASSIGNMENTS_NOT_RECORDED;

    static ReviewSubjectScope of(@Nullable ReviewSubjectRow row, Set<Long> participants) {
        if (row == null || row.assignments() == 0) {
            return row != null && !participants.containsAll(row.developers())
                    ? INCLUDES_NON_PARTICIPANT
                    : ASSIGNMENTS_NOT_RECORDED;
        }
        if (!participants.containsAll(row.developers())) return INCLUDES_NON_PARTICIPANT;
        if (row.unattributedAssignments() > 0
                || row.unattributedResults() > 0
                || row.developers().isEmpty()) {
            return ATTRIBUTION_UNKNOWN;
        }
        return ALL_SUBJECTS_PARTICIPATING;
    }
}
