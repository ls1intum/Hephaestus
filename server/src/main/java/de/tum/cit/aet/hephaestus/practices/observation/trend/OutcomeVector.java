package de.tum.cit.aet.hephaestus.practices.observation.trend;

import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Presence;

/** Complete presence-assessment outcome distribution for one or more evidence opportunities. */
public record OutcomeVector(
    int demonstratedStrengths,
    int safeAvoidances,
    int commissionProblems,
    int omissionGaps,
    int notApplicable
) {
    static final OutcomeVector EMPTY = new OutcomeVector(0, 0, 0, 0, 0);

    public OutcomeVector {
        if (
            demonstratedStrengths < 0 ||
            safeAvoidances < 0 ||
            commissionProblems < 0 ||
            omissionGaps < 0 ||
            notApplicable < 0
        ) {
            throw new IllegalArgumentException("Outcome counts must be non-negative");
        }
    }

    /**
     * {@code NOT_APPLICABLE} and {@code INCONCLUSIVE} both land in {@code notApplicable}: they are different
     * facts for a reader — "there was nothing here to judge" versus "the practice looked and could not tell" —
     * but neither is an outcome, so neither may move a trend in either direction. Only the surfaces that
     * explain a review distinguish them; the trend counts them alike as an opportunity that produced no verdict.
     */
    public static OutcomeVector of(Presence presence, Assessment assessment) {
        if (presence == Presence.NOT_APPLICABLE || presence == Presence.INCONCLUSIVE) {
            if (assessment != null) {
                throw new IllegalArgumentException(presence + " must not carry an assessment");
            }
            return new OutcomeVector(0, 0, 0, 0, 1);
        }
        if (assessment == null) {
            throw new IllegalArgumentException("Applicable presence requires an assessment");
        }
        return switch (presence) {
            case PRESENT -> assessment == Assessment.GOOD
                ? new OutcomeVector(1, 0, 0, 0, 0)
                : new OutcomeVector(0, 0, 1, 0, 0);
            case ABSENT -> assessment == Assessment.GOOD
                ? new OutcomeVector(0, 1, 0, 0, 0)
                : new OutcomeVector(0, 0, 0, 1, 0);
            case NOT_APPLICABLE, INCONCLUSIVE -> throw new IllegalStateException("Handled above");
        };
    }

    public OutcomeVector plus(OutcomeVector other) {
        return new OutcomeVector(
            demonstratedStrengths + other.demonstratedStrengths,
            safeAvoidances + other.safeAvoidances,
            commissionProblems + other.commissionProblems,
            omissionGaps + other.omissionGaps,
            notApplicable + other.notApplicable
        );
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
