package de.tum.cit.aet.hephaestus.agent.proxy.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationDecision;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationEligibility;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ConversationEligibilityDTO(
        ConversationEligibility.State state, Instant readAt, List<ResultDecisionDTO> results) {
    public ConversationEligibilityDTO {
        results = List.copyOf(results);
        if (state != ConversationEligibility.State.COMPLETE && !results.isEmpty())
            throw new IllegalArgumentException("Unavailable conversation routing cannot authorize results");
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ResultDecisionDTO(
            UUID resultId,
            ConversationDecision decision,
            @Nullable FeedbackStopReason stopReason) {}
}
