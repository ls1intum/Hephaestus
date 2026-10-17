package de.tum.cit.aet.hephaestus.practices.spi;

import org.jspecify.annotations.Nullable;

public record FeedbackDecisionInput(
        FeedbackDecisionAction action,
        @Nullable FeedbackDecisionReason reason,
        @Nullable String note,
        @Nullable String contentDigest) {}
