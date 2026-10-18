package de.tum.cit.aet.hephaestus.practices.spi;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * What a decision committed. A refused approval records no decision but keeps its committed check, and for a changed
 * publication context also the withholding, so the caller can report the state that actually persisted.
 */
public record FeedbackDecisionOutcome(
        @Nullable UUID decisionId, @Nullable FeedbackStopReason refusal, FeedbackState state) {
    public FeedbackDecisionOutcome {
        if ((decisionId == null) == (refusal == null))
            throw new IllegalArgumentException("An outcome is a recorded decision or a recorded refusal.");
    }
}
