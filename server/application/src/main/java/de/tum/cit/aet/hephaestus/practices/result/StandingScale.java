package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.spi.PracticeStanding;

// STRENGTH needs a clear majority; DEVELOPING identifies where attention could help.
public final class StandingScale {

    /**
     * Positive share ABOVE which the subject reads as a strength. Short of unanimity, so a dozen practices are
     * not held back forever by whichever single one is currently amber.
     */
    static final double STRENGTH_SHARE = 0.8;

    /**
     * Positive share AT or above which the subject still reads as mixed rather than developing.
     *
     * <p>Placed between ONE setback and a PATTERN of them. Under the standing's recency weights a single
     * problem on the newest piece of reviewed work scores {@code 0.384} and two in a row score {@code 0.138}; a boundary at
     * one half put both on the same side, so one slip after a clean run read as "needs attention". Both scores
     * hold for one to four decided pieces of work, since a practice's share is measured against the weight of a
     * full window ({@code PracticeTrend#recentMetShare}), so the boundary needs no second value for a short history.
     */
    static final double MIXED_SHARE = 0.37;

    private StandingScale() {}

    public static boolean isVerdict(PracticeStanding standing) {
        return standing == PracticeStanding.STRENGTH
                || standing == PracticeStanding.MIXED
                || standing == PracticeStanding.DEVELOPING;
    }

    static int rank(PracticeStanding standing) {
        return switch (standing) {
            case DEVELOPING -> 0;
            case STRENGTH -> 1;
            case MIXED -> 2;
            case NOT_OBSERVED -> 3;
            case NO_OPPORTUNITY -> 4;
        };
    }

    /**
     * Classifies a positive share in {@code [0,1]}. The caller must know there is evidence at all: this scale
     * cannot tell "half of it went badly" from "we know nothing", so 0.0 must never stand in for the latter.
     */
    static PracticeStanding classify(double positiveShare) {
        if (positiveShare > STRENGTH_SHARE) {
            return PracticeStanding.STRENGTH;
        }
        if (positiveShare >= MIXED_SHARE) {
            return PracticeStanding.MIXED;
        }
        return PracticeStanding.DEVELOPING;
    }
}
