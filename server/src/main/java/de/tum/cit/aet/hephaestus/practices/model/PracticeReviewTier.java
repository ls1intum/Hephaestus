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
 * <p>This exists because the only previous answer to "this practice is too noisy" was to stop using it
 * in new reviews, which also stopped the measurement and put a hole in the time series. {@link #MEASURE}
 * is the tier that separates the two: the review still runs and every observation is still recorded, so
 * the behaviour series is unbroken, and nobody is told anything. Semgrep (<em>Monitor / Comment /
 * Block</em>), Kyverno (<em>Audit / Enforce</em>) and ESLint (<em>off / warn / error</em>) all draw the
 * same line.
 *
 * <p><strong>What {@link #COACH} actually reaches, stated plainly.</strong> "Quiet channels" is, today,
 * exactly one channel: {@link FeedbackChannel#CONVERSATION}, a turn in the recipient's mentor
 * conversation. {@link FeedbackChannel#PROFILE} — the private reflection surface — is declared in the
 * channel vocabulary and <em>nothing in the application writes it</em>; there is no producer. Describing
 * COACH as "profile and mentor" would therefore be a claim the code cannot honour, of exactly the kind
 * this branch has spent its time deleting. So COACH is defined as, and documented as, conversation-only.
 * When a PROFILE producer is built, it joins COACH without the tier's meaning changing: COACH has always
 * meant "every quiet channel that exists".
 */
public enum PracticeReviewTier {
    /** Not reviewed at all. No review is started, so there is no observation and no feedback. */
    OFF,

    /**
     * Reviewed, recorded, silent. The review runs and its observations land in the behaviour series;
     * no feedback unit is delivered on any channel. This is the tier for a practice worth tracking that
     * is not worth interrupting anyone about — and the only way to turn a practice down without also
     * turning its measurement off.
     */
    MEASURE,

    /**
     * Reviewed, recorded, and raised in the recipient's mentor conversation — never on the artifact
     * itself. See the class javadoc: "quiet channels" is conversation-only today because PROFILE has no
     * producer.
     */
    COACH,

    /** Reviewed, recorded, and delivered everywhere: on the artifact and in the conversation. */
    ENGAGE;

    /** Longest constant name; the {@code review_tier} column and its check constraint are sized from this. */
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
            // Conversation is the quiet channel COACH was named for.
            case CONVERSATION -> this == COACH || this == ENGAGE;
            // Declared but unwritten: no code path produces a PROFILE unit, so answering anything other
            // than "not deliverable" would describe a delivery that cannot happen. When a producer
            // exists, this joins CONVERSATION on the COACH side.
            case PROFILE -> false;
        };
    }
}
