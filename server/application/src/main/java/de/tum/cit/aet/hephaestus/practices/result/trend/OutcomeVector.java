package de.tum.cit.aet.hephaestus.practices.result.trend;

import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;

/** Counts every recorded result; abstentions do not change the decided denominator. */
public record OutcomeVector(int met, int notMet, int doesNotApply, int couldNotDetermine) {
    static final OutcomeVector EMPTY = new OutcomeVector(0, 0, 0, 0);

    public OutcomeVector {
        if (met < 0 || notMet < 0 || doesNotApply < 0 || couldNotDetermine < 0) {
            throw new IllegalArgumentException("ResultOutcome counts must be non-negative");
        }
    }

    public static OutcomeVector of(ResultOutcome outcome) {
        return switch (outcome) {
            case MET -> new OutcomeVector(1, 0, 0, 0);
            case NOT_MET -> new OutcomeVector(0, 1, 0, 0);
            case DOES_NOT_APPLY -> new OutcomeVector(0, 0, 1, 0);
            case COULD_NOT_DETERMINE -> new OutcomeVector(0, 0, 0, 1);
        };
    }

    public OutcomeVector plus(OutcomeVector other) {
        return new OutcomeVector(
                met + other.met,
                notMet + other.notMet,
                doesNotApply + other.doesNotApply,
                couldNotDetermine + other.couldNotDetermine);
    }

    public int decided() {
        return met + notMet;
    }

    public double metShare() {
        if (decided() == 0) {
            throw new IllegalStateException("Undecided results have no met share");
        }
        return (double) met / decided();
    }
}
