package de.tum.cit.aet.hephaestus.agent.job;

/**
 * Every reason a review can record for skipping a practice.
 *
 * <p>The readiness report records these at two levels — one for a source that failed its requirement,
 * one for a practice whose own settings run no model — and this is the single vocabulary the API
 * publishes for both. {@code PracticeEvidenceSkipReasonTest} holds it to that union.
 */
public enum PracticeEvidenceSkipReason {
    SOURCE_NOT_AVAILABLE,
    SOURCE_INCOMPLETE,
    SOURCE_NOT_CURRENT,
    SOURCE_EMPTY,
    NO_AUTOMATED_REVIEW,
    DECLARED_EVIDENCE_INSUFFICIENT,
}
