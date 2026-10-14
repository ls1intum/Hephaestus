package de.tum.cit.aet.hephaestus.agent.proxy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.PracticePageDecision;
import de.tum.cit.aet.hephaestus.practices.spi.PracticePageSupport;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

public sealed interface PracticePageSupportDTO {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Complete(PracticePageSupport.State state, Instant readAt, List<PracticeSupportDTO> practices)
            implements PracticePageSupportDTO {
        public Complete {
            if (state != PracticePageSupport.State.COMPLETE)
                throw new IllegalArgumentException("Support is not complete");
            practices = List.copyOf(practices);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record Closed(
            PracticePageSupport.State state,
            Instant readAt,
            @Nullable String refusal) implements PracticePageSupportDTO {
        public Closed {
            if (state == PracticePageSupport.State.COMPLETE)
                throw new IllegalArgumentException("Complete support requires practices");
        }
    }

    record PracticeSupportDTO(
            String practiceSlug, PracticePageDecision decision, List<SupportOccurrenceDTO> occurrences) {
        public PracticeSupportDTO {
            occurrences = List.copyOf(occurrences);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record SupportOccurrenceDTO(
            UUID resultId,
            String itemKind,
            String itemId,
            ResultOutcome outcome,
            ReviewOrigin origin,
            String summary,
            @Nullable String explanation,
            @Nullable JsonNode evidence,
            @Nullable String practiceVersionId,
            Instant recordedAt) {}
}
