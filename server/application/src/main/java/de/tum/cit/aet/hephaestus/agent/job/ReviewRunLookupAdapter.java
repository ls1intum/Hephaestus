package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.handler.PracticeCoverageLedger;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository.ReviewRunFactsRow;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRunLookup;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class ReviewRunLookupAdapter implements ReviewRunLookup {

    private final AgentJobRepository repository;
    private final ReviewRunTargets targets;

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Target> findTargets(long workspaceId, Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        return targets.of(workspaceId, repository.findReviewRunTargets(workspaceId, jobIds));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ReviewRunFacts> findFacts(long workspaceId, Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        List<ReviewRunFactsRow> rows = repository.findReviewRunFacts(workspaceId, jobIds);
        Map<UUID, Target> named = targets.of(workspaceId, rows);
        return rows.stream().collect(Collectors.toUnmodifiableMap(ReviewRunFactsRow::getId, row -> {
            Target target = Objects.requireNonNull(named.get(row.getId()));
            return new ReviewRunFacts(
                    target,
                    AgentJobReviewRunStates.of(row.getStatus()),
                    row.getTriggerMode(),
                    PracticeCoverageLedger.from(row.getOutput()).evaluated(),
                    feedbackUrl(row, target),
                    row.getCreatedAt(),
                    row.getLlmModel(),
                    row.getCompletedAt(),
                    phase(row.getStatus()),
                    row.getStartedAt(),
                    AgentJob.holdWhileQueued(row.getStatus(), row.getHoldReason()),
                    row.getStatus() == AgentJobStatus.COMPLETED ? conclusion(row) : null);
        }));
    }

    /** Exhaustive, so a new job status fails the build here rather than a lineage read at run time. */
    private static ReviewRunPhase phase(AgentJobStatus status) {
        return switch (status) {
            case QUEUED -> ReviewRunPhase.QUEUED;
            case RUNNING -> ReviewRunPhase.RUNNING;
            case COMPLETED -> ReviewRunPhase.COMPLETED;
            case FAILED -> ReviewRunPhase.FAILED;
            case TIMED_OUT -> ReviewRunPhase.TIMED_OUT;
            case CANCELLED -> ReviewRunPhase.CANCELLED;
        };
    }

    private static ReviewRunConclusion conclusion(ReviewRunFactsRow row) {
        return switch (ReviewRunOutcome.fromJobOutput(row.getOutput())) {
            case REVIEWED -> ReviewRunConclusion.REVIEWED;
            case INSUFFICIENT_EVIDENCE -> ReviewRunConclusion.INSUFFICIENT_EVIDENCE;
            case COALESCED -> ReviewRunConclusion.COALESCED;
            case SUPERSEDED -> ReviewRunConclusion.SUPERSEDED;
        };
    }

    /** The run's summary comment, linked only when the recorded address is a comment on the reviewed work itself. */
    private static @Nullable String feedbackUrl(ReviewRunFactsRow row, Target target) {
        return DeliveredWorkFeedbackService.verifiedLink(target.url(), target.provider(), row.getFeedbackUrl())
                .orElse(null);
    }
}
