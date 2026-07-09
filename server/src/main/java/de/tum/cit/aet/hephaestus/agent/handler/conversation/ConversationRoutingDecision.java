package de.tum.cit.aet.hephaestus.agent.handler.conversation;

/**
 * The outcome of routing one observation for conversational delivery. Only {@link #ADMIT} is prepared as a PREPARED
 * CONVERSATION feedback unit; every other value is a named, testable reason the observation is NOT raised in a mentor
 * turn.
 */
public enum ConversationRoutingDecision {
    /** Author-targeted problem with no natural inline anchor, not already delivered in-context - prepare it. */
    ADMIT,
    /** Not a problem worth coaching on (a strength, or a NOT_APPLICABLE abstention). */
    NOT_DELIVERABLE,
    /** Has a natural inline anchor (a diff location on a PR) - it belongs in-context, not in the conversation. */
    HAS_INLINE_ANCHOR,
    /** The same locus (recurrence_key) was already DELIVERED in-context to this recipient - do not re-raise it. */
    ALREADY_DELIVERED_IN_CONTEXT,
    /** Reviewer-targeted - deferred (ADR-0021-C2). */
    REVIEWER_DEFERRED,
}
