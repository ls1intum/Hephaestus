package de.tum.cit.aet.hephaestus.practices.spi;

import org.jspecify.annotations.Nullable;

public record FeedbackCompositionContext(
        @Nullable PracticePageSupport practicePageSupport,
        @Nullable ConversationEligibility conversationEligibility) {}
