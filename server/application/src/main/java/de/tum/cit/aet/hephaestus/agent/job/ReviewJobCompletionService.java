package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.spi.ProcessingStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewCancellationRequested;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewCompletion;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStopReason;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/** Settles runtime processing independently from product proof and physical-copy delivery. */
@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewJobCompletionService {
    private static final Logger log = LoggerFactory.getLogger(ReviewJobCompletionService.class);
    private final ReviewJobRepository jobs;
    private final ReviewJobAssignments assignments;
    private final ReviewAssignmentStore members;
    private final ReviewService reviews;
    private final ReviewJobLifecycleService lifecycle;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "practice-job-completion", lockAtMostFor = "PT2M", lockAtLeastFor = "PT1S")
    public void tick() {
        for (UUID id : jobs.findTerminalForProcessing(100))
            try {
                transactions.executeWithoutResult(status -> settle(id));
            } catch (RuntimeException failure) {
                log.warn(
                        "Review processing could not settle: jobId={}, failure={}",
                        id,
                        failure.getClass().getSimpleName());
            }
    }

    @TransactionalEventListener
    public void cancel(ReviewCancellationRequested event) {
        for (var job : jobs.findByReviewIdAndWorkspaceIdOrderByAvailableAtAsc(event.reviewId(), event.workspaceId()))
            if (job.getStatus() == ReviewJobStatus.QUEUED || job.getStatus() == ReviewJobStatus.RUNNING)
                lifecycle.cancel(event.workspaceId(), job.getId());
    }

    private void settle(UUID id) {
        var retained = jobs.findById(id).orElse(null);
        if (retained == null) return;
        var job = jobs.findByIdAndWorkspaceIdForUpdate(id, retained.getWorkspaceId())
                .orElse(null);
        if (job == null
                || job.getStatus() == ReviewJobStatus.QUEUED
                || job.getStatus() == ReviewJobStatus.RUNNING
                || (job.getProcessingStatus() != ProcessingStatus.NONE
                        && job.getProcessingStatus() != ProcessingStatus.PENDING)) return;
        var review = assignments.review(job);
        boolean admitted = ResultAdmissionService.isAdmitted(job);
        var reason = job.getExecutionStartedAt() == null
                ? ReviewStopReason.PREPARATION_FAILED
                : ReviewStopReason.EXECUTION_FAILED;
        if (!admitted)
            for (var assignment : assignments.admissible(job))
                members.fail(
                        job.getWorkspaceId(),
                        assignment.id(),
                        reason,
                        "The execution ended without an admitted result");
        boolean completed = job.getStatus() == ReviewJobStatus.COMPLETED;
        job.setProcessingStatus(completed ? ProcessingStatus.DONE : ProcessingStatus.FAILED);
        jobs.saveAndFlush(job);
        if (review.kind() == ReviewKind.SCAN) return;
        var status = job.getStatus() == ReviewJobStatus.CANCELLED
                ? ReviewStatus.CANCELLED
                : completed
                        ? ReviewStatus.COMPLETE
                        : admitted || job.getExecutionStartedAt() != null ? ReviewStatus.PARTIAL : ReviewStatus.FAILED;
        var stop = completed
                ? null
                : job.getStatus() == ReviewJobStatus.TIMED_OUT
                        ? ReviewStopReason.TIMED_OUT
                        : job.getStatus() == ReviewJobStatus.CANCELLED ? ReviewStopReason.CANCELLED : reason;
        reviews.complete(
                job.getWorkspaceId(),
                review.id(),
                new ReviewCompletion(status, stop, clock.instant(), job.getAvailableAt()));
    }
}
