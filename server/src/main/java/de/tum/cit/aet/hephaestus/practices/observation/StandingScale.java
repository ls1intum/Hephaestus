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
     * Positive share AT or above which the subject still reads as mixed rather than developing. At exactly one
     * half sits the evenly balanced case — a practice whose reviewed work was half positive, or an area whose
     * every practice is itself mixed — and it belongs on the side that acknowledges both.
     */
    static final double MIXED_SHARE = 0.5;

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
