package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.profile.dto.ReviewPracticeOutcomesDTO;
import de.tum.cit.aet.hephaestus.practices.profile.dto.SlippedPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewFeedbackCountsDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewSubjectDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewResultCountsDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@Schema(description = "A review run with result and feedback outcome counts")
public record ReviewRunSummaryDTO(
        @NonNull UUID id,
        @NonNull AgentJobStatus status,
        @NonNull ReviewRunOutcome reviewOutcome,

        @Schema(description = DeliveryStatus.DESCRIPTION) @Nullable
        DeliveryStatus resultProcessing,

        @NonNull ReviewRunTargetDTO target,
        @NonNull Instant createdAt,
        @NonNull ReviewResultCountsDTO practiceResults,
        @NonNull ReviewFeedbackCountsDTO feedback,

        @Schema(
                description = "What occasioned the run: LIVE as the work arrived, MANUAL when somebody asked for it,"
                        + " BACKFILL while catching up on work that predates the connection; absent when the run"
                        + " records an origin this release does not know")
        @Nullable
        ResultOrigin origin,

        @NonNull
        @Schema(
                description = "The developers the run is about: everyone it recorded a result about, or, before"
                        + " it recorded any, the person its work names; empty when neither is known")
        List<ReviewSubjectDTO> developers,

        @NonNull ReviewPracticeOutcomesDTO practices,

        @NonNull
        @Schema(description = "Every practice counted in practices.notMet, once each, in order of practice name")
        List<SlippedPracticeDTO> slippedPractices,

        @Schema(description = "How many practices the run measured; absent when it wrote no coverage ledger") @Nullable
        Integer practicesEvaluated,

        @Schema(
                description = "The run's place among the practice reviews of its work, counted from 1 for the oldest;"
                        + " absent when the run names no work")
        @Nullable
        Integer positionOnWork,

        @Schema(
                description = "How many practice reviews its work has in all, so a row can say \"Review 2 of 3\";"
                        + " absent when the run names no work")
        @Nullable
        Integer reviewsOnWork,

        @Schema(
                description = "Why a queued review waits where an admin can undo it, such as BUDGET when it is held"
                        + " for the monthly LLM cap; absent unless the review is queued and held")
        @Nullable
        String holdReason,

        @NonNull
        @Schema(
                description = "True when a pull request or merge request review completed after its work was merged,"
                        + " as the timing's finishedAfterMerge counts it")
        Boolean finishedAfterMerge,

        @NonNull
        @Schema(
                description = "True when finishedAfterMerge and the merge itself started the review, so it could only"
                        + " finish after it")
        Boolean finishedAfterMergeStartedByMerge) {}
