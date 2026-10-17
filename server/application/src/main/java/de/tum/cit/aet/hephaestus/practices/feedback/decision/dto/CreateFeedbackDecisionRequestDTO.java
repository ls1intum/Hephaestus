package de.tum.cit.aet.hephaestus.practices.feedback.decision.dto;

import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record CreateFeedbackDecisionRequestDTO(
        @NotNull FeedbackDecisionAction action,
        @Nullable FeedbackDecisionReason reasonCode,
        @Nullable @Size(max = 500) String note,
        @Nullable @Pattern(regexp = "^[0-9a-f]{64}$") String contentDigest) {}
