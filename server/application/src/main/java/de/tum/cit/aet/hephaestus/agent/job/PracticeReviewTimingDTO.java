package de.tum.cit.aet.hephaestus.agent.job;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(
        description = "Where the time of practice reviews went in a time range, stage by stage, and the reviews that"
                + " need a look")
public record PracticeReviewTimingDTO(
        @NonNull @Schema(description = "Inclusive lower bound of the range measured, with its default filled in")
        Instant from,

        @NonNull @Schema(description = "Exclusive upper bound of the range measured, with its default filled in")
        Instant to,

        @NonNull
        @Schema(
                description = "Until asked: from the moment on the work that started the review to its request, over"
                        + " the reviews a moment started before their request")
        ReviewStageTimingDTO asked,

        @NonNull
        @Schema(
                description = "From the request until a worker claimed the review; a retried review's earlier"
                        + " attempt counts as waiting")
        ReviewStageTimingDTO queue,

        @NonNull @Schema(description = "From the claim until the model started, while the evidence was prepared")
        ReviewStageTimingDTO preparation,

        @NonNull
        @Schema(
                description =
                        "From the start of the model until the review ended, failed and timed-out runs" + " included")
        ReviewStageTimingDTO model,

        @NonNull
        @Schema(
                description =
                        "From acceptance of the review on the work (the end of the review for legacy runs) until it first posted feedback by itself;"
                                + " feedback that waited for an admin's approval is left out")
        ReviewStageTimingDTO posted,

        @NonNull
        @Schema(
                description = "From the moment that started the review, or its request where no moment did, until"
                        + " it first posted feedback on the work by itself, one figure per review over the reviews"
                        + " that did")
        ReviewStageTimingDTO total,

        @Schema(
                description =
                        "Each stage's part of the time of the reviews that total measures; public execution ends at acceptance, even while private composition continues; absent when no"
                                + " review posted a comment")
        @Nullable
        ReviewTimeSharesDTO totalShares,

        @NonNull
        @Schema(
                description = "From the moment that started the review, or its request where no moment did, until"
                        + " it completed, one figure per review over the reviews that completed, whether they"
                        + " commented or not")
        ReviewStageTimingDTO finished,

        @Schema(
                description = "Each stage's part of the time of the reviews that finished measures; absent when no"
                        + " review completed")
        @Nullable
        ReviewTimeSharesDTO finishedShares,

        @NonNull
        @Schema(
                description = "Not a stage of the review: from the end of the review until a developer first read its"
                        + " feedback on their practice pages or was shown it in a conversation")
        ReviewStageTimingDTO read,

        @NonNull
        @Schema(
                description =
                        "Practice reviews queued now for longer than " + PracticeReviewTimingService.LONG_WAIT_MINUTES
                                + " minutes, whenever they were requested, other than those held for budget")
        Long waitingLong,

        @NonNull
        @Schema(
                description = "Pull request and merge request reviews requested in the range that completed after"
                        + " the work was merged. The review list returns the same reviews with finishedAfterMerge.")
        Long finishedAfterMerge,

        @NonNull @Schema(description = "Of those, the reviews the merge itself started")
        Long finishedAfterMergeStartedByMerge,

        @NonNull
        @Schema(
                description = "Feedback created in the range and withheld because the work was already merged"
                        + " (ARTIFACT_MERGED)")
        Long feedbackWithheldAsMerged) {}
