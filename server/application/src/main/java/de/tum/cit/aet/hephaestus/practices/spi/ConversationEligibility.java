package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    public record ResultDecision(UUID resultId, ConversationDecision decision) {}
}
