package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.ReviewQueueItemRow;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.ReviewQueueRow;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewSubjectResolver;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReviewSubjectDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup.Target;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The practice review queue of one workspace as it stands now: what runs, what waits and what is held for budget,
 * the reviews at its head, and how many reviews finished in the last hour and day. Up to four bounded reads: the
 * counts in one pass over the queued and running jobs and the finished ones of the last day, the head as one page,
 * and the work's titles and the developers of that page.
 */
@Service
@RequiredArgsConstructor
class PracticeReviewQueueService {

    static final int HEAD_SIZE = 5;

    private final AgentJobRepository agentJobRepository;
    private final ReviewRunTargets reviewRunTargets;
    private final ReviewSubjectResolver subjectResolver;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PracticeReviewQueueDTO queue(long workspaceId) {
        Instant now = clock.instant();
        ReviewQueueRow counts = agentJobRepository.findReviewQueue(
                workspaceId,
                AgentPurpose.PRACTICE_REVIEW,
                AgentJob.HOLD_REASON_BUDGET,
                now,
                now.minus(Duration.ofHours(1)),
                now.minus(Duration.ofDays(1)));
        List<ReviewQueueItemRow> head = agentJobRepository.findReviewQueueHead(
                workspaceId,
                AgentPurpose.PRACTICE_REVIEW,
                AgentJob.HOLD_REASON_BUDGET,
                now,
                PageRequest.of(0, HEAD_SIZE));
        return new PracticeReviewQueueDTO(
                now,
                counts.getRunning(),
                counts.getActiveWorkers(),
                counts.getWaiting(),
                counts.getOldestWaitingSince(),
                counts.getHeldForBudget(),
                items(workspaceId, head),
                counts.getFinishedLastHour(),
                counts.getFinishedLastDay(),
                counts.getFinishedLastDay() / 24.0);
    }

    private List<PracticeReviewQueueItemDTO> items(long workspaceId, List<ReviewQueueItemRow> head) {
        if (head.isEmpty()) {
            return List.of();
        }
        Map<UUID, Target> targets = reviewRunTargets.of(workspaceId, head);
        Map<UUID, @Nullable Long> developerIds = new HashMap<>();
        head.forEach(
                row -> developerIds.put(row.getId(), ReviewRunSummaryQueryService.namedSubjectId(row.getMetadata())));
        Map<Long, ReviewSubjectDTO> developers = subjectResolver.resolve(developerIds.values());
        return head.stream()
                .map(row -> {
                    boolean running = row.getStatus() == AgentJobStatus.RUNNING;
                    boolean held = !running && AgentJob.HOLD_REASON_BUDGET.equals(row.getHoldReason());
                    Long developerId = developerIds.get(row.getId());
                    return new PracticeReviewQueueItemDTO(
                            row.getId(),
                            row.getStatus(),
                            held,
                            ReviewRunTargetDTO.from(Objects.requireNonNull(targets.get(row.getId()))),
                            developerId == null ? null : developers.get(developerId),
                            since(row, running, held));
                })
                .toList();
    }

    /**
     * Since when a review of the head is in its status: a running one from its start; a waiting one from when it
     * became available to a worker, its request or its requeue; one held for budget from its request, since the
     * hold moves its availability to the next check of the cap, a time still to come.
     */
    private static Instant since(ReviewQueueItemRow row, boolean running, boolean held) {
        if (running) {
            return Objects.requireNonNullElse(row.getStartedAt(), row.getCreatedAt());
        }
        return held ? row.getCreatedAt() : row.getAvailableAt();
    }
}
