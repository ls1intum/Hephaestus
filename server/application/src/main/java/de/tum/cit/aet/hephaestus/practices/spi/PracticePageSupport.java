package de.tum.cit.aet.hephaestus.practices.spi;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public record PracticePageSupport(
        State state,
        Instant readAt,
        List<PracticeSupport> practices,
        @Nullable String refusal) {
    public enum State {
        COMPLETE,
        UNAVAILABLE,
        REFUSED
    }

    public PracticePageSupport {
        practices = List.copyOf(practices);
        if (state != State.COMPLETE && !practices.isEmpty())
            throw new IllegalArgumentException("Unavailable support cannot carry evidence.");
    }

    public record PracticeSupport(String practiceSlug, PracticePageDecision decision, List<Occurrence> occurrences) {
        public PracticeSupport {
            occurrences = List.copyOf(occurrences);
            if (decision != PracticePageDecision.ADMIT && !occurrences.isEmpty())
                throw new IllegalArgumentException("Unadmitted support cannot carry evidence.");
        }
    }

    public record Occurrence(
            UUID resultId,
            String itemKind,
            String itemId,
            ResultOutcome outcome,
            ReviewOrigin origin,
            String summary,
            @Nullable String explanation,
            @Nullable JsonNode evidence,
            @Nullable String practiceVersionId,
            Instant recordedAt) {
        public Occurrence {
            if (outcome != ResultOutcome.NOT_MET)
                throw new IllegalArgumentException("Pattern support requires a proved problem.");
            evidence = evidence == null ? null : evidence.deepCopy();
        }

        @Override
        public @Nullable JsonNode evidence() {
            return evidence == null ? null : evidence.deepCopy();
        }
    }
}
