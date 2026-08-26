package de.tum.cit.aet.hephaestus.practices.feedback;

public enum FeedbackDeliveryState {
    AWAITING_APPROVAL,
    PREPARED,
    PARTIALLY_DELIVERED,
    PARTIALLY_FAILED,
    DELIVERED,
    SUPERSEDED,
    SUPPRESSED,
    FAILED,
    DISCARDED,
}
