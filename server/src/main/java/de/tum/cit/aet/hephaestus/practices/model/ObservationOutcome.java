package de.tum.cit.aet.hephaestus.practices.model;

import org.jspecify.annotations.Nullable;

/**
 * What one observation says about the developer, derived from {@link Presence} × {@link Assessment}.
 *
 * <p>Presence and assessment are orthogonal measurement axes (ADR 0022) and neither alone answers "what
 * happened here": {@code GOOD} means a demonstrated behaviour on a PRESENT row and an avoided trap on an
 * ABSENT one, which call for different responses. This enum is that combination named once, so the mapping
 * lives in a single place instead of being re-derived by every reader.
 *
 * <p>Derived, never persisted — {@code observation} stores the two axes and this reads them, so the two can
 * never drift apart.
 *
 * <p>The four applicable outcomes are <b>categories, not ordered proficiency levels</b>. Nothing here says a
 * safe avoidance is worth less than a demonstrated strength; both are positive evidence. What the distinction
 * selects is the mentoring response, per
 * {@code docs/contributor/practice-proficiency-and-trends.md} § Observation outcome.
 */
public enum ObservationOutcome {
    /** The practice's behaviour was there and it was right — reinforce it against the concrete evidence. */
    DEMONSTRATED_STRENGTH,
    /** A harmful behaviour could have appeared and did not — acknowledge without claiming mastery. */
    SAFE_AVOIDANCE,
    /** Something harmful was done — explain the consequence and suggest a correction. */
    COMMISSION_PROBLEM,
    /** Something needed was left out — scaffold the missing step. */
    OMISSION_GAP,
    /**
     * No verdict: the work offered nothing to judge, or the practice looked and could not tell. Both
     * {@link Presence#NOT_APPLICABLE} and {@link Presence#INCONCLUSIVE} land here — different facts for a
     * reader, but neither is an outcome, so neither may move a trend or a standing in either direction.
     */
    NOT_APPLICABLE;

    /**
     * The outcome of one observation's two axes.
     *
     * @throws IllegalArgumentException if the pair violates the coherence the DB CHECK
     *     {@code chk_observation_presence_assessment} enforces — an assessment is required exactly for a
     *     presence that {@link Presence#carriesValence() carries valence}.
     */
    public static ObservationOutcome of(Presence presence, @Nullable Assessment assessment) {
        if (presence.carriesValence() != (assessment != null)) {
            throw new IllegalArgumentException(
                "Assessment is required exactly for a presence that carries valence (presence=" +
                    presence +
                    ", assessment=" +
                    assessment +
                    ")"
            );
        }
        return switch (presence) {
            case PRESENT -> assessment == Assessment.GOOD ? DEMONSTRATED_STRENGTH : COMMISSION_PROBLEM;
            case ABSENT -> assessment == Assessment.GOOD ? SAFE_AVOIDANCE : OMISSION_GAP;
            case NOT_APPLICABLE, INCONCLUSIVE -> NOT_APPLICABLE;
        };
    }

    /** The outcome of an observation, read off its own two axes. */
    public static ObservationOutcome of(Observation observation) {
        return of(observation.getPresence(), observation.getAssessment());
    }

    /** Evidence in the developer's favour. */
    public boolean isPositive() {
        return this == DEMONSTRATED_STRENGTH || this == SAFE_AVOIDANCE;
    }

    /** Evidence against, and the reason a practice has something to work on. */
    public boolean isNegative() {
        return this == COMMISSION_PROBLEM || this == OMISSION_GAP;
    }

    /** Whether this outcome is a verdict at all — the opportunity grain a trend counts. */
    public boolean isApplicable() {
        return this != NOT_APPLICABLE;
    }

    /**
     * Whether a defect-detector practice may claim this outcome as a strength.
     *
     * <p>Such a practice hunts an undesirable behaviour, so {@link #DEMONSTRATED_STRENGTH} is incoherent for
     * it — what would be demonstrated is the defect. {@link #SAFE_AVOIDANCE} is the opposite case and is
     * exactly what a clean detector run proves: the behaviour could have appeared in the corpus the practice
     * bounds and did not.
     */
    public boolean isCoherentStrengthFor(boolean defectDetector) {
        return this == SAFE_AVOIDANCE || (this == DEMONSTRATED_STRENGTH && !defectDetector);
    }
}
