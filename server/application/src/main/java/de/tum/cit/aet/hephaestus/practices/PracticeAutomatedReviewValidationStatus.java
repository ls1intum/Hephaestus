package de.tum.cit.aet.hephaestus.practices;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Who stands behind a practice's automated-review policy.
 *
 * <p>One value, because one is all anything can produce: nothing in the product validates a policy
 * independently, so every practice that ships is its author's declaration and says so. A status the API
 * can never return would be a claim about this system that is not true of it — and the day an
 * independent validation exists it arrives with the provenance that makes it checkable, not before.
 */
@Schema(description = "Independent status; evidence authors cannot promote their own requirements")
public enum PracticeAutomatedReviewValidationStatus {
    AUTHOR_DECLARED,
}
