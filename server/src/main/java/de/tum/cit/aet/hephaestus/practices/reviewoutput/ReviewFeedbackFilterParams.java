package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackQueryFilter;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
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
    /**
     * The kind as the bare string it is on the wire, parsed rather than bound.
     *
     * <p>A typed parameter here is rendered by springdoc as {@code artifactKind.value} — it walks into
     * the record — so a generated client would send a query key no caller ever writes. The grammar is
     * still enforced, one line below, where a malformed value becomes a 400 that names itself instead of
     * a binding failure.
     */
    @Parameter(description = "Kind of reviewed work, e.g. scm.pull_request")
    @RequestParam(required = false)
    @Nullable
    String artifactKind,
    @Parameter(description = "Artifact ID; requires artifactKind")
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
        if (artifactId != null && artifactKind == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "artifactId requires artifactKind");
        }
        ArtifactKind kind = parseArtifactKind();
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        return new FeedbackQueryFilter(
            deliveryState,
            suppressionReason,
            channel,
            agentJobId,
            kind,
            artifactId,
            recipientUserId,
            from,
            to
        );
    }

    private @Nullable ArtifactKind parseArtifactKind() {
        if (artifactKind == null) {
            return null;
        }
        try {
            return ArtifactKind.of(artifactKind);
        } catch (IllegalArgumentException malformed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, malformed.getMessage(), malformed);
        }
    }
}
