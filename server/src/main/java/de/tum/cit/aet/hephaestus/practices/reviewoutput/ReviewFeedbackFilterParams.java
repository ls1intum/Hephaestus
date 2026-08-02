package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackQueryFilter;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

public record ReviewFeedbackFilterParams(
    @RequestParam(required = false) @Nullable List<FeedbackDeliveryState> deliveryState,
    @RequestParam(required = false) @Nullable List<FeedbackSuppressionReason> suppressionReason,
    @RequestParam(required = false) @Nullable List<FeedbackChannel> channel,
    @RequestParam(required = false) @Nullable UUID agentJobId,
    @RequestParam(required = false) @Nullable WorkArtifact artifactType,
    @Parameter(description = "Artifact ID; requires artifactType")
    @RequestParam(required = false)
    @Positive
    @Nullable
    Long artifactId,
    @RequestParam(required = false) @Positive @Nullable Long recipientUserId,
    @Parameter(description = "Inclusive lower bound")
    @RequestParam(required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Nullable
    Instant from,
    @Parameter(description = "Exclusive upper bound")
    @RequestParam(required = false)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @Nullable
    Instant to
) {
    public FeedbackQueryFilter toFilter() {
        if (artifactId != null && artifactType == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artifactId requires artifactType");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        return new FeedbackQueryFilter(
            deliveryState,
            suppressionReason,
            channel,
            agentJobId,
            artifactType,
            artifactId,
            recipientUserId,
            from,
            to
        );
    }
}
