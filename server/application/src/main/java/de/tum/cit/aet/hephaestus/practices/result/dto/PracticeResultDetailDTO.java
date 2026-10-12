package de.tum.cit.aet.hephaestus.practices.result.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.ReviewClaimCurrentness;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository.ResultFeedback;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackResponseDTO;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResultInvalidation;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * The one result shape a developer reads: what was observed, why it was noted, what to try next, the
 * evidence behind it and the developer's own response to the feedback that carried it. Served on its own by
 * the result detail endpoint and per result by the review-run feed, so a feed row never needs a
 * second request to open.
 *
 * <p>Intentionally omits internal fields: {@code PracticeResult.agentJobId}, {@code PracticeResult.occurrenceKey},
 * and the raw {@code PracticeResult.aboutUserId}.
 */
@Schema(description = "Full practice result detail including delivered feedback and evidence")
public record PracticeResultDetailDTO(
        @NonNull @Schema(description = "Result ID") UUID id,
        @NonNull @Schema(description = "Practice slug") String practiceSlug,
        @NonNull @Schema(description = "Practice name") String practiceName,

        @NonNull @Schema(description = "Artifact type (e.g. PULL_REQUEST)")
        ArtifactKind artifactKind,

        @NonNull @Schema(description = "Artifact entity ID") Long artifactId,

        @NonNull @Schema(description = "Result summary") String summary,

        @NonNull @Schema(description = "Result of the practice review")
        Outcome outcome,

        @Nullable @Schema(description = "Severity level (null unless outcome is NOT_MET)")
        Severity severity,

        @Nullable PracticeResultEvidenceDTO evidence,

        @Nullable @Schema(description = "Evidence-based rationale for the result")
        String evidenceRationale,

        @Nullable
        @Schema(
                description = "The summary body of the newest eligible feedback about this result to this developer "
                        + "(null when no summary body was recorded, including delivery through inline notes only)")
        String deliveredFeedback,

        @Nullable
        @Schema(
                description = "The next step the review wrote about this result, whether or not the "
                        + "feedback carrying it was delivered (null when it wrote none)")
        String nextStep,

        @Nullable
        @Schema(
                description =
                        "The developer's standing answer and response handle for the newest eligible feedback about "
                                + "this result, including inline-only delivery with no summary body (null when no "
                                + "eligible feedback exists or the newest eligible feedback has failed delivery)")
        FeedbackResponseDTO feedbackResponse,

        @Nullable
        @Schema(
                description = "Links to the comments on the reviewed work that carry the feedback behind "
                        + "feedbackResponse, summary first (null when that feedback was not delivered on the work "
                        + "or the platform returned no link)")
        List<String> feedbackCommentUrls,

        @NonNull
        @Schema(description = "The located problems of a NOT_MET result, in display order; empty for any other outcome")
        List<ObservationDTO> observations,

        @Nullable @Schema(description = "One place of the work that shows the practice done well; null when none")
        PracticeResultExampleDTO example,

        @Nullable
        @Schema(
                description =
                        "How much of its scope the review read; null for a result recorded before coverage was kept")
        PracticeResultCoverageDTO coverage,

        @NonNull ReviewClaimCurrentness claimCurrentness,

        @Nullable
        @Schema(
                description = "When a workspace admin marked this result as incorrect; null while it stands. "
                        + "An invalidated result counts toward nothing current.")
        Instant invalidatedAt,

        @Nullable @Schema(description = "The admin's reason for the invalidation; null while the result stands")
        String invalidationReason,

        @NonNull @Schema(description = "What occasioned the measurement; never mix origins in one trend line")
        ResultOrigin origin,

        @Nullable
        @Schema(description = "Link to the reviewed artifact on its platform (null when it cannot be resolved)")
        String artifactUrl,

        @NonNull @Schema(description = "When the result was made")
        Instant observedAt) {

    /**
     * One piece of feedback answers both {@code deliveredFeedback} and {@code feedbackResponse}, so the
     * developer answers the feedback they received, even when it landed only as inline notes. FAILED feedback's text is still shown — it was composed
     * and may have reached them on the artifact — but it carries no response handle, because only DELIVERED
     * feedback can be answered. {@code feedbackCommentUrls} are the comments of that same delivered feedback.
     */
    public static PracticeResultDetailDTO from(
            PracticeResult practiceResult,
            @Nullable ResultFeedback feedback,
            @Nullable List<String> feedbackCommentUrls,
            @Nullable String nextStep,
            @Nullable String artifactUrl,
            boolean includeEvidence,
            @Nullable PracticeResultInvalidation invalidation,
            List<Observation> problems) {
        var practice = practiceResult.getPractice();
        return new PracticeResultDetailDTO(
                practiceResult.getId(),
                practice.getSlug(),
                practice.getName(),
                practiceResult.getArtifactKind(),
                practiceResult.getArtifactId(),
                practiceResult.getSummary(),
                practiceResult.getOutcome(),
                practiceResult.getSeverity(),
                includeEvidence ? PracticeResultEvidenceDTO.from(practiceResult.getEvidence(), problems) : null,
                practiceResult.getEvidenceRationale(),
                feedback == null ? null : feedback.getBody(),
                nextStep,
                responseTo(feedback),
                feedbackCommentUrls,
                includeEvidence ? problems.stream().map(ObservationDTO::from).toList() : List.of(),
                includeEvidence ? PracticeResultExampleDTO.from(practiceResult.getExample()) : null,
                PracticeResultCoverageDTO.from(practiceResult.getCoverage()),
                ReviewClaimCurrentness.of(
                        practiceResult.getPracticeRevision(), practice, practiceResult.getSupersededAt()),
                invalidation == null ? null : invalidation.getInvalidatedAt(),
                invalidation == null ? null : invalidation.getReason(),
                practiceResult.getOrigin(),
                artifactUrl,
                practiceResult.getObservedAt());
    }

    /** No handle, no response: feedback that failed to deliver cannot be answered, so it carries none. */
    private static @Nullable FeedbackResponseDTO responseTo(@Nullable ResultFeedback feedback) {
        if (feedback == null) {
            return null;
        }
        UUID feedbackId = feedback.getFeedbackId();
        return feedbackId == null ? null : FeedbackResponseDTO.from(feedbackId, feedback);
    }
}
