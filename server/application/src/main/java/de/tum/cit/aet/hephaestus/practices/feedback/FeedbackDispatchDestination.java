package de.tum.cit.aet.hephaestus.practices.feedback;

/**
 * Practice-feedback provider creates require a durable dispatch identity. Re-derivable idempotent
 * updates may remain direct only when failures enter a durable retry.
 */
public enum FeedbackDispatchDestination {
    AUTOMATIC_REVIEW_PACKAGE,
    APPROVED_REVIEW_PACKAGE,
}
