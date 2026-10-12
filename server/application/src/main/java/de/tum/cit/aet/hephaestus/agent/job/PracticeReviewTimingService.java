package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.ReviewTimingSignalsQuery;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.ReviewTimingSignalsRow;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.StageSecondsRow;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.StageTimingRow;
import de.tum.cit.aet.hephaestus.core.time.TimeRange;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Where the time of practice reviews went in a range, measured over the reviews created in it as the overview counts
 * them: how long each stage took, from the moment on the work that started a review to its request on, how long
 * whole reviews took from that moment, or the request where none started it, until their comment was posted or
 * until they completed, with each stage's part of that same set of reviews, how long until a developer read the
 * feedback, and the reviews that need a look. Queue waits and budget holds are counted as they stand now.
 */
@Service
@RequiredArgsConstructor
class PracticeReviewTimingService {

    static final int LONG_WAIT_MINUTES = 10;

    private final AgentJobRepository agentJobRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PracticeReviewTimingDTO timing(long workspaceId, TimeRange range) {
        List<StageTimingRow> rows = agentJobRepository.findStageTimings(
                workspaceId, AgentPurpose.PRACTICE_REVIEW, range.from(), range.to());
        Map<String, ReviewStageTimingDTO> stages =
                rows.stream().collect(Collectors.toMap(StageTimingRow::getStage, PracticeReviewTimingService::stage));
        ReviewTimingSignalsRow signals = agentJobRepository.findTimingSignals(
                new ReviewTimingSignalsQuery(
                        workspaceId,
                        AgentPurpose.PRACTICE_REVIEW,
                        range.from(),
                        range.to(),
                        clock.instant().minus(Duration.ofMinutes(LONG_WAIT_MINUTES)),
                        AgentJob.HOLD_REASON_BUDGET),
                ScmSignals.PULL_REQUEST_MERGED.value());
        // Every row carries the same sums; no row means no review was measured, so there are no shares.
        @Nullable StageSecondsRow seconds = rows.isEmpty() ? null : rows.getFirst();
        Function<String, ReviewStageTimingDTO> stage = name -> stages.getOrDefault(name, ReviewStageTimingDTO.EMPTY);
        return new PracticeReviewTimingDTO(
                range.from(),
                range.to(),
                stage.apply("ASKED"),
                stage.apply("QUEUE"),
                stage.apply("PREPARATION"),
                stage.apply("MODEL"),
                stage.apply("POSTED"),
                stage.apply("TOTAL"),
                seconds == null
                        ? null
                        : ReviewTimeSharesDTO.of(
                                seconds.getCommentedAsked(),
                                seconds.getCommentedQueue(),
                                seconds.getCommentedPreparation(),
                                seconds.getCommentedModel(),
                                seconds.getCommentedUnsplit(),
                                seconds.getCommentedPosted()),
                stage.apply("FINISHED"),
                seconds == null
                        ? null
                        : ReviewTimeSharesDTO.of(
                                seconds.getFinishedAsked(),
                                seconds.getFinishedQueue(),
                                seconds.getFinishedPreparation(),
                                seconds.getFinishedModel(),
                                seconds.getFinishedUnsplit(),
                                null),
                stage.apply("READ"),
                signals.getWaitingLong(),
                signals.getFinishedAfterMerge(),
                signals.getFinishedAfterMergeStartedByMerge(),
                signals.getFeedbackWithheldAsMerged());
    }

    private static ReviewStageTimingDTO stage(StageTimingRow row) {
        return new ReviewStageTimingDTO(row.getCount(), row.getMedian(), row.getP90());
    }
}
