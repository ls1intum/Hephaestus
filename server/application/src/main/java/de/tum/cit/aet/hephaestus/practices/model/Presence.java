package de.tum.cit.aet.hephaestus.practices.model;

/**
 * Whether the target signal a practice looks for was seen in the developer's work, was expected but
 * absent, does not apply at all, or could not be settled from evidence that was in fact present
 * (ADR 0022).
 *
 * <p><b>Measurement, not evaluation.</b> Presence states only what the detector <em>saw</em>; the good/bad
 * direction is the orthogonal {@link Assessment}, resolved per observation against the practice criteria.
 * A present good behaviour is a strength, a present bad behaviour is a problem, an absent good behaviour is
 * a gap.
 *
 * <p>Also orthogonal to {@link Severity}, which is meaningful only for a {@link Assessment#BAD} observation.
 *
 * <p>"We could not look" is deliberately not representable here: a source that was missing, errored, or
 * governance-blocked produces an {@code AutomatedReviewReadinessDecision} on the review and no
 * {@link Observation} at all.
 */
public enum Presence {
    PRESENT,
    ABSENT,
    /** E.g. no network calls in the diff means error-state-handling does not apply. */
    NOT_APPLICABLE,
    /**
     * The evidence needed was present and read, and does not settle the question either way; carries no
     * valence, so {@link Assessment} is null.
     *
     * <p>Chosen over {@link #NOT_APPLICABLE} when the detector could not decide: that value claims the work
     * has no subject here, so using it under uncertainty would enter the behaviour series as "nothing to
     * see". For a practice with an {@code exhaustive} stance, ABSENT is warranted only when the corpus
     * searched was whole; an incomplete corpus returns INCONCLUSIVE instead.
     */
    INCONCLUSIVE;

    /**
     * Whether {@link Assessment} is required (true) or forbidden (false) for an observation with this
     * presence — the same predicate as the DB CHECK {@code chk_observation_presence_assessment}.
     */
    public boolean carriesValence() {
        return this == PRESENT || this == ABSENT;
    }
}
