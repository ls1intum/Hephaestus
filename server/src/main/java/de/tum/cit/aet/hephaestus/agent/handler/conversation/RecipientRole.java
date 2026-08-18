package de.tum.cit.aet.hephaestus.agent.handler.conversation;

/**
 * Whose craft a conversational feedback unit is about, as the router sees it. Only {@link #AUTHOR}-targeted feedback
 * is delivered conversationally today. Reviewer-targeted delivery is an EXPLICIT deferral behind ADR 0021 reviewer
 * attribution: the router returns {@link ConversationRoutingDecision#REVIEWER_DEFERRED} rather than silently dropping
 * it, so the deferral is visible and testable.
 */
public enum RecipientRole {
    AUTHOR,
    REVIEWER,
}
