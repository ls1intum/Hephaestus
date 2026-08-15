package de.tum.cit.aet.hephaestus.practices.feedback;

public enum FeedbackDeliveryState {
    /**
     * Decided, but not yet received by the developer. What is already written varies by channel and the
     * state deliberately does not say: a CONVERSATION unit carries a NULL body because the mentor writes
     * the words at delivery, while a REFLECTION unit carries its composed body because there is no later
     * actor to write them. The one thing PREPARED asserts on every lane is that the recipient has not
     * had it yet.
     */
    PREPARED,
    /**
     * Received by the developer. On the two lanes we do not own this is inferred from a successful post;
     * on REFLECTION it is observed, at the moment they open it.
     */
    DELIVERED,
    /** Replaced by a newer row that points back via {@code replaces_id}; kept for the temporal record. */
    SUPERSEDED,
    /** Deliberately withheld; the reason is in {@code suppression_reason}. */
    SUPPRESSED,
    /** Delivery was attempted but failed. */
    FAILED,
}
