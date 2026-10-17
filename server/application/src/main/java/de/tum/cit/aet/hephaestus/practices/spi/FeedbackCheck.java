package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public record FeedbackCheck(
        FeedbackCheckStage stage,
        boolean allowed,
        @Nullable FeedbackStopReason reason,
        Instant checkedAt,
        FeedbackCheckFacts facts) {}
