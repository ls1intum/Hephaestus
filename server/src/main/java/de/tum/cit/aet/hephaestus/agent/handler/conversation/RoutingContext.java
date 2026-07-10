package de.tum.cit.aet.hephaestus.agent.handler.conversation;

/**
 * The audience context for one conversational-routing pass. Carries the {@link RecipientRole} the observations in
 * this pass are addressed to (see {@link RecipientRole} for the ADR-0021-C2 reviewer deferral). Reviewer attribution
 * is not built yet, so the listener drives every pass with {@link #author()}.
 */
public record RoutingContext(RecipientRole recipientRole) {
    public RoutingContext {
        if (recipientRole == null) {
            throw new IllegalArgumentException("recipientRole must not be null");
        }
    }

    /** Author-targeted routing - the only role delivered conversationally today. */
    public static RoutingContext author() {
        return new RoutingContext(RecipientRole.AUTHOR);
    }

    /** Reviewer-targeted routing - deferred (ADR-0021-C2). */
    public static RoutingContext reviewer() {
        return new RoutingContext(RecipientRole.REVIEWER);
    }
}
