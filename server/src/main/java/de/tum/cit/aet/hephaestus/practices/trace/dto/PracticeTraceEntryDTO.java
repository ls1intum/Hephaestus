package de.tum.cit.aet.hephaestus.practices.trace.dto;

import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.trace.PracticeTraceOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * One practice's answer for one artifact, on two axes that must not be collapsed.
 *
 * <p>{@code outcome} is about the <em>measurement</em>: did this practice get assessed, and if not,
 * why. The counts and {@code withheldReasons} are about the <em>intervention</em>: did anything reach
 * a person. A practice at {@code MEASURE} is {@code REVIEWED} with nothing delivered, and reporting
 * that as one number would hide the exact distinction loudness tiers exist to make.
 */
@Schema(description = "What became of one practice on this artifact, and whether anyone heard about it")
public record PracticeTraceEntryDTO(
    @NonNull String practiceSlug,
    @NonNull String practiceName,
    @NonNull
    @Schema(description = "How loudly the workspace currently runs this practice")
    PracticeReviewTier reviewTier,
    @NonNull PracticeTraceOutcome outcome,
    @NonNull @Schema(description = "The outcome in a sentence, phrased as what would change it") String explanation,
    @NonNull @Schema(description = "The signals this practice watches") List<SignalName> watches,
    @Schema(description = "The occurrence this answer is about; null when nothing it watches happened")
    SignalName occasionedBy,
    @Schema(
        description = "That occurrence's id in this trace's signals list. The name alone cannot identify " +
            "it — the same signal recurs on every revision — so this is what a link should follow."
    )
    UUID occasionedById,
    @Schema(description = "When the answer was settled") Instant decidedAt,
    @Schema(description = "The review this answer came from, when one ran") UUID reviewId,
    @NonNull @Schema(description = "Measurements this practice produced on this artifact") Integer observationCount,
    @NonNull @Schema(description = "Interventions actually delivered to a person") Integer deliveredCount,
    @NonNull
    @Schema(
        description = "Why prepared feedback was withheld. Non-empty with observations present means we " +
            "measured and deliberately said nothing."
    )
    List<FeedbackSuppressionReason> withheldReasons
) {}
