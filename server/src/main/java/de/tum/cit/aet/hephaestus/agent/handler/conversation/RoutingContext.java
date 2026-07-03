package de.tum.cit.aet.hephaestus.agent.handler.conversation;

/**
 * The audience context for one conversational-routing pass. Carries the {@link RecipientRole} the observations in
 * this pass are addressed to, so the {@link FeedbackChannelRouter} can apply the ADR-0021-C2 reviewer deferral as an
 * explicit guard rather than a silent omission. Reviewer attribution is not built yet, so the listener drives every
 * pass with {@link #author()}.
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

    /** Reviewer-targeted routing - every observation defers behind ADR-0021-C2 until reviewer attribution lands. */
    public static RoutingContext reviewer() {
        return new RoutingContext(RecipientRole.REVIEWER);
    }
}
