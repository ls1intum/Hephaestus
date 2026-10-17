package de.tum.cit.aet.hephaestus.practices.spi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ConversationEligibility(State state, Instant readAt, List<ResultDecision> results) {
    public enum State {
        COMPLETE,
        UNAVAILABLE,
        REFUSED
    }

    public ConversationEligibility {
        results = List.copyOf(results);
        if (state != State.COMPLETE && !results.isEmpty())
            throw new IllegalArgumentException("Unavailable routing cannot authorize results.");
    }

    public record ResultDecision(
            UUID resultId,
            ConversationDecision decision,

            @JsonInclude(JsonInclude.Include.ALWAYS) @Nullable
            FeedbackStopReason stopReason) {
        public ResultDecision {
            if (decision == ConversationDecision.GATE_REFUSED && stopReason == null)
                throw new IllegalArgumentException("A gate refusal needs its recorded reason.");
        }
    }
}
