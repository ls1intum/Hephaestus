package de.tum.cit.aet.hephaestus.practices.feedback;

public enum FeedbackDeliveryState {
    /** Composed and immutable, but requires an authorized human decision before it can enter delivery. */
    AWAITING_APPROVAL,
    /**
     * Decided, but not yet received by the developer. What is already written varies by channel and the
     * state deliberately does not say: an IN_CHAT unit carries a NULL body because the mentor writes
     * the words at delivery, while an IN_APP unit carries its composed body because there is no later
     * actor to write them. The one thing PREPARED asserts on every lane is that the recipient has not
     * had it yet.
     */
    PREPARED,
    /** A provider received part of an approved package, while one or more placements remain unsent. */
    PARTIALLY_DELIVERED,
    /**
     * Received by the developer. On the two lanes we do not own this is inferred from a successful post;
     * on IN_APP it is observed, at the moment they open it.
     */
    DELIVERED,
    /** Replaced by a newer row that points back via {@code replaces_id}; kept for the temporal record. */
    SUPERSEDED,
    /** Deliberately withheld; the reason is in {@code suppression_reason}. */
    SUPPRESSED,
    /** Delivery was attempted but failed. */
    FAILED,
    /** An authorized reviewer deliberately rejected this exact composed unit. */
    DISCARDED,
}
