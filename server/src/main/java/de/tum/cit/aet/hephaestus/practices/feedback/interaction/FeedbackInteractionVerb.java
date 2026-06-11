package de.tum.cit.aet.hephaestus.practices.feedback.interaction;

/**
 * The action a user took on feedback — the "verb" of an actor–verb–object interaction event,
 * modelled after xAPI / IMS Caliper interaction vocabularies (we borrow the immutable
 * actor-verb-object shape, not the JSON-LD wire format). Distinguishes low-signal telemetry
 * (viewed, dwelled) from higher-signal engagement (replied, resolved). Explicit verdicts on a
 * finding (applied / disputed) are NOT here — those are {@code FindingReaction}.
 */
public enum FeedbackInteractionVerb {
    VIEWED,
    EXPANDED,
    DWELLED,
    NAVIGATED,
    REPLIED,
    RESOLVED,
    REACTED,
    /** A contributor turn in a mentor conversation (the scaffolding shown is not a delivery). */
    MENTOR_TURN,
    /** A facilitator acted on the evidence (e.g. reached out to a contributor). */
    INTERVENED,
}
