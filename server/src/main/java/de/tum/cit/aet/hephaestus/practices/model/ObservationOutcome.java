package de.tum.cit.aet.hephaestus.practices.model;

import org.jspecify.annotations.Nullable;

/**
 * What one observation says about the developer, read off {@link Presence} × {@link Assessment} (ADR 0022).
 *
 * <p>Derived, never persisted, so the axes and their reading cannot drift apart. The matrix is read here and
 * nowhere else.
 *
 * <p>The four applicable outcomes are categories, not ordered levels: a safe avoidance is not worth less than
 * a demonstrated strength. What the distinction selects is the mentoring response.
 */
public enum ObservationOutcome {
    /** The behaviour was there and it was right. Reinforce it against the concrete evidence. */
    DEMONSTRATED_STRENGTH,
    /** A harmful behaviour could have appeared and did not. Acknowledge without claiming mastery. */
    SAFE_AVOIDANCE,
    /** Something harmful was done. Explain the consequence and suggest a correction. */
    COMMISSION_PROBLEM,
    /** Something needed was left out. Scaffold the missing step. */
    OMISSION_GAP,
    /**
     * No verdict: the work offered nothing to judge, or the practice looked and could not tell. Both
     * {@link Presence#NOT_APPLICABLE} and {@link Presence#INCONCLUSIVE} land here. Different facts for a
     * reader, but neither is an outcome, so neither may move a trend or a standing.
     */
    NOT_APPLICABLE;

    /**
     * The outcome of one observation's two axes.
     *
     * @throws IllegalArgumentException if the pair violates the coherence the DB CHECK
     *     {@code chk_observation_presence_assessment} enforces: an assessment is required exactly for a
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

    /** Whether this outcome is a verdict at all, which is the grain a trend counts. */
    public boolean isApplicable() {
        return this != NOT_APPLICABLE;
    }

    /**
     * Whether a defect-detector practice may claim this outcome as a strength.
     *
     * <p>Such a practice hunts an undesirable behaviour, so {@link #DEMONSTRATED_STRENGTH} is incoherent for
     * it, since what would be demonstrated is the defect. {@link #SAFE_AVOIDANCE} is the opposite case and is
     * exactly what a clean detector run proves: the behaviour could have appeared in the corpus the practice
     * bounds and did not.
     */
    public boolean isCoherentStrengthFor(boolean defectDetector) {
        return this == SAFE_AVOIDANCE || (this == DEMONSTRATED_STRENGTH && !defectDetector);
    }
}
