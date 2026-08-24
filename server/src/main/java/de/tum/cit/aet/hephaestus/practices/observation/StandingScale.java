package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.practices.observation.dto.ReflectionPracticeDTO;

/**
 * The one qualitative scale both the practice and the area standing are read off.
 *
 * <p>Both levels answer the same question — "what share of this was positive?" — and differ only in what they
 * aggregate: a practice averages the outcomes of its recent evidence opportunities, an area averages the
 * standings of its practices. Keeping a single classification here is what makes the hierarchy coherent: an
 * area cannot apply a stricter or looser bar than the cards it is built from, and a reader who has understood
 * one level has understood both.
 *
 * <p>The thresholds are deliberately asymmetric. {@code STRENGTH} needs a strict majority well past the middle
 * because it is a claim about the developer; {@code DEVELOPING} needs only the absence of one because it is a
 * pointer to where attention would pay off, not a verdict. Everything between is honestly mixed.
 */
final class StandingScale {

    /**
     * Positive share ABOVE which the subject reads as a strength. Short of unanimity on purpose: an area of a
     * dozen practices would otherwise be held back forever by whichever single one is currently amber, and a
     * surface that can never say "this is going well" stops being read.
     */
    static final double STRENGTH_SHARE = 0.8;

    /**
     * Positive share AT or above which the subject still reads as mixed rather than developing.
     *
     * <p>Placed to separate ONE setback from a PATTERN of them, which is where the meaning changes. With the
     * standing's recency weights a single problem on the newest work item, everything before it clean, scores
     * {@code 0.384}; two problems in a row score {@code 0.138}. A boundary at one half put both on the same
     * side, so one slip after a clean run read as "needs attention" — for a developer working at an 80%
     * success rate that fired on one review in five, while the trend beside it stayed silent because a single
     * item is no evidence of a change. The boundary now sits between those two cases.
     *
     * <p>That makes the scale symmetric in the unit it reasons about: two clean opportunities in a row earn a
     * strength, two problems in a row cost one, and a single event of either kind moves the standing without
     * settling it.
     */
    static final double MIXED_SHARE = 0.37;

    private StandingScale() {}

    /**
     * Classifies a positive share in {@code [0,1]}. Callers are responsible for there being any evidence at
     * all: this scale cannot distinguish "half of it went badly" from "we know nothing", and a caller with no
     * evidence must say so in its own vocabulary rather than let 0.0 read as a verdict.
     */
    static ReflectionPracticeDTO.Standing classify(double positiveShare) {
        if (positiveShare > STRENGTH_SHARE) {
            return ReflectionPracticeDTO.Standing.STRENGTH;
        }
        if (positiveShare >= MIXED_SHARE) {
            return ReflectionPracticeDTO.Standing.MIXED;
        }
        return ReflectionPracticeDTO.Standing.DEVELOPING;
    }
}
