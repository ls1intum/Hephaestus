package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackLocation;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackQueryFilter;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.web.QueryFilterSupport;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @param artifactKind a bare string, not an {@link ArtifactKind} — {@link QueryFilterSupport#artifactKind} has the
 *     reason, and parses it in {@link #toFilter()}, where a malformed value becomes a 400
 */
public record ReviewFeedbackFilterParams(
        @RequestParam(required = false) @Nullable List<FeedbackDeliveryState> deliveryState,
        @RequestParam(required = false) @Nullable List<FeedbackSuppressionReason> suppressionReason,
        @RequestParam(required = false) @Nullable List<FeedbackChannel> channel,

        @Parameter(
                description = "Where the feedback appears, by the provider that shows it (repeatable): a channel split"
                        + " where the provider is known")
        @RequestParam(required = false)
        @Nullable
        List<FeedbackLocation> location,

        @RequestParam(required = false) @Nullable UUID agentJobId,

        @Parameter(description = "Keep the feedback written from this observation")
        @RequestParam(required = false)
        @Nullable
        UUID observationId,

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

        @Parameter(
                description = "Practices whose feedback to list (repeatable): feedback bound to an observation of any"
                        + " of them, in any role")
        @RequestParam(required = false)
        @Nullable
        List<String> practiceSlug,

        @Parameter(
                description = "Practice groups whose feedback to list (repeatable): feedback bound to an observation"
                        + " of a practice in any of them")
        @RequestParam(required = false)
        @Nullable
        List<String> groupSlug,

        @Parameter(description = "Inclusive lower bound")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable
        Instant from,

        @Parameter(description = "Exclusive upper bound")
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Nullable
        Instant to) {
    public FeedbackQueryFilter toFilter() {
        ArtifactKind kind = QueryFilterSupport.artifactKind(artifactKind);
        QueryFilterSupport.requireKindForItem(kind, artifactId);
        QueryFilterSupport.requireWindow(from, to);
        return new FeedbackQueryFilter(
                deliveryState,
                suppressionReason,
                channel,
                location,
                agentJobId,
                observationId,
                kind,
                artifactId,
                recipientUserId,
                practiceSlug,
                groupSlug,
                from,
                to);
    }
}
