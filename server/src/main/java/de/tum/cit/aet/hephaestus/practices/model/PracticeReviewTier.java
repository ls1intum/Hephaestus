package de.tum.cit.aet.hephaestus.practices.model;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;

/**
 * How loud one practice is allowed to be in one workspace — the settable form of the system's own
 * principle that a measurement and an intervention are separate decisions.
 *
 * <p>The tiers are ordered by how far a practice's result is allowed to travel, and each tier adds to
 * the one before it:
 *
 * <table>
 *   <caption>Loudness tiers</caption>
 *   <tr><th>Tier</th><th>Review runs</th><th>Conversation</th><th>On the artifact</th></tr>
 *   <tr><td>{@link #OFF}</td>     <td>no</td>  <td>no</td>  <td>no</td></tr>
 *   <tr><td>{@link #MEASURE}</td> <td>yes</td> <td>no</td>  <td>no</td></tr>
 *   <tr><td>{@link #COACH}</td>   <td>yes</td> <td>yes</td> <td>no</td></tr>
 *   <tr><td>{@link #ENGAGE}</td>  <td>yes</td> <td>yes</td> <td>yes</td></tr>
 * </table>
 *
 * <p>{@link #MEASURE} is the tier that separates measurement from intervention: the review runs and
 * every observation is recorded, so the behaviour series stays unbroken, and nobody is told anything.
 * Silencing a practice by dropping it from new reviews instead puts a hole in that series. Semgrep
 * (<em>Monitor / Comment / Block</em>), Kyverno (<em>Audit / Enforce</em>) and ESLint (<em>off / warn /
 * error</em>) all draw the same line.
 */
public enum PracticeReviewTier {
    /** Not reviewed at all. No review is started, so there is no observation and no feedback. */
    OFF,

    /**
     * Reviewed, recorded, silent — the only way to turn a practice down without turning its measurement
     * off. No feedback unit is delivered on any channel.
     */
    MEASURE,

    /** Reviewed, recorded, and raised in the recipient's mentor conversation — never on the artifact. */
    COACH,

    /** Reviewed, recorded, and delivered everywhere: on the artifact and in the conversation. */
    ENGAGE;

    /** Width of the {@code review_tier} column; every constant name must fit. */
    public static final int MAX_LENGTH = 16;

    /** The tier a practice starts at, and the tier a workspace that never touches this setting stays at. */
    public static final PracticeReviewTier DEFAULT = ENGAGE;

    /** Whether a review may be started for this practice at all — the detection gate's admission test. */
    public boolean admitsReview() {
        return this != OFF;
    }

    /**
     * Whether a finding from this practice may be delivered on {@code channel}.
     *
     * <p>The single definition point for "is this practice allowed to speak here". Both delivery paths
     * ask this rather than comparing tiers themselves, so a new channel or a new tier is one edit.
     */
    public boolean delivers(FeedbackChannel channel) {
        return switch (channel) {
            case IN_CONTEXT -> this == ENGAGE;
            case CONVERSATION -> this == COACH || this == ENGAGE;
            // Declared but unwritten: nothing produces a PROFILE unit, so any other answer would describe
            // a delivery that cannot happen. A producer would put this on the COACH side.
            case PROFILE -> false;
        };
    }
}
