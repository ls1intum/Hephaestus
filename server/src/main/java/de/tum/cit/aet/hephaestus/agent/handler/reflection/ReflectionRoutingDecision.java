package de.tum.cit.aet.hephaestus.agent.handler.reflection;

/**
 * The outcome of routing one composed message for the reflection surface. Only {@link #ADMIT} becomes a
 * PREPARED REFLECTION feedback unit; every other value is a named, testable reason the developer is not
 * shown it.
 *
 * <p>One refusal vocabulary per stage — this is the reflection lane's, as
 * {@code ConversationRoutingDecision} is the conversation lane's. A refusal that means the same thing on
 * both lanes still gets its own constant here, because the two stages answer to different rules and a
 * shared enum would make every future divergence look like a bug.
 */
public enum ReflectionRoutingDecision {
    /** A corroborated, live, tier-admitted pattern the developer has not been shown lately — prepare it. */
    ADMIT,

    /**
     * The composed message named a practice this recipient has no measurement of in the window, so
     * there is nothing to stand behind it. A message with no evidence is not feedback.
     */
    NO_EVIDENCE,

    /**
     * Fewer than {@link ReflectionFeedbackRouter#CORROBORATION_ARTIFACTS} distinct artifacts carry the
     * problem. One occurrence is a task-level note and belongs on the work, not on a surface whose whole
     * claim is "this recurs".
     */
    UNCORROBORATED,

    /** The practice's autonomy tier (OFF or PROPOSE) does not admit the reflection lane. */
    PRACTICE_TIER_QUIET,

    /**
     * Every measurement behind the message came from a backfill campaign. Backfill is sound as a
     * snapshot and, by {@code ObservationOrigin}'s own words, "unusable as a trend against LIVE rows" —
     * and a process-level message is exactly a trend claim. Held back deliberately rather than
     * structurally: the entitlement in {@code ObservationOrigin} still admits the lane, so lifting this is
     * one line and one decision, taken with a real workspace in front of you.
     */
    BACKFILL_HELD,

    /**
     * The recipient was shown a message about this practice inside the cooldown. A pattern does not
     * change week to week, and repeating it turns a private surface into nagging.
     */
    RECENTLY_SURFACED,

    /**
     * The practice's occasion is about somebody other than the artifact's author, so the observations
     * bound to it may be filed against the wrong person. Refused by name rather than shown: the private
     * view is the surface that would make the misattribution visible, to the one person it is not about.
     * Lifts when reviewer attribution exists.
     */
    REVIEWER_ATTRIBUTED,

    /** The composed message arrived without a body or a next step, so there is nothing to deliver. */
    INCOMPLETE,

    /**
     * Over the per-cycle cap for this recipient. Written as a SUPPRESSED row rather than dropped, so
     * "withheld" never reads as "ignored".
     */
    VOLUME_CAPPED,
}
