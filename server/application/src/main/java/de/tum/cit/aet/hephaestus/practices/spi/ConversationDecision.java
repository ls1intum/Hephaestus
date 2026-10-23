package de.tum.cit.aet.hephaestus.practices.spi;

public enum ConversationDecision {
    ADMIT,
    FEEDBACK_OFF,
    MENTOR_UNAVAILABLE,
    GATE_REFUSED,
    UNATTRIBUTABLE_SUBJECT,
    // No longer decided: kept so that stored composition traces with these values still read.
    HAS_INLINE_ANCHOR,
    ALREADY_DELIVERED_ON_WORK,
    REVIEWER_DEFERRED,
    BACKFILL_QUIET
}
