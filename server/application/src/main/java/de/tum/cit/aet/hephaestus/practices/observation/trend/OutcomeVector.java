package de.tum.cit.aet.hephaestus.practices.observation.trend;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOutcome;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import org.jspecify.annotations.Nullable;

/** Complete presence-assessment outcome distribution for one or more evidence opportunities. */
public record OutcomeVector(
        int demonstratedStrengths, int safeAvoidances, int commissionProblems, int omissionGaps, int notApplicable) {
    static final OutcomeVector EMPTY = new OutcomeVector(0, 0, 0, 0, 0);

    public OutcomeVector {
        if (demonstratedStrengths < 0
                || safeAvoidances < 0
                || commissionProblems < 0
                || omissionGaps < 0
                || notApplicable < 0) {
            throw new IllegalArgumentException("Outcome counts must be non-negative");
        }
    }

    /** The one-hot vector for a single observation's two measurement axes. */
    public static OutcomeVector of(Presence presence, @Nullable Assessment assessment) {
        return of(ObservationOutcome.of(presence, assessment));
    }

    /**
     * The one-hot vector for one observation's outcome — this record is that outcome counted rather than
     * named, so the two stay in step by construction.
     *
     * <p>{@code NOT_APPLICABLE} covers both {@link Presence#NOT_APPLICABLE} and {@link Presence#INCONCLUSIVE}:
     * they are different facts for a reader — "there was nothing here to judge" versus "the practice looked
     * and could not tell" — but neither is an outcome, so neither may move a trend in either direction. Only
     * the surfaces that explain a review distinguish them; the trend counts them alike as an opportunity that
     * produced no verdict.
     */
    public static OutcomeVector of(ObservationOutcome outcome) {
        return switch (outcome) {
            case DEMONSTRATED_STRENGTH -> new OutcomeVector(1, 0, 0, 0, 0);
            case SAFE_AVOIDANCE -> new OutcomeVector(0, 1, 0, 0, 0);
            case COMMISSION_PROBLEM -> new OutcomeVector(0, 0, 1, 0, 0);
            case OMISSION_GAP -> new OutcomeVector(0, 0, 0, 1, 0);
            case NOT_APPLICABLE -> new OutcomeVector(0, 0, 0, 0, 1);
        };
    }

    public OutcomeVector plus(OutcomeVector other) {
        return new OutcomeVector(
                demonstratedStrengths + other.demonstratedStrengths,
                safeAvoidances + other.safeAvoidances,
                commissionProblems + other.commissionProblems,
                omissionGaps + other.omissionGaps,
                notApplicable + other.notApplicable);
    }

    public int positives() {
        return demonstratedStrengths + safeAvoidances;
    }

    public int negatives() {
        return commissionProblems + omissionGaps;
    }

    public int applicable() {
        return positives() + negatives();
    }

    public double positiveShare() {
        if (applicable() == 0) {
            throw new IllegalStateException("An inapplicable-only vector has no positive share");
        }
        return (double) positives() / applicable();
    }
}
