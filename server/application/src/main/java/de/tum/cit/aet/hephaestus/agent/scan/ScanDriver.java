package de.tum.cit.aet.hephaestus.agent.scan;

import de.tum.cit.aet.hephaestus.agent.job.ReviewJobLifecycleService;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobStateConflictException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewCompletion;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewQueue;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewScanStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStopReason;
import de.tum.cit.aet.hephaestus.practices.spi.ScanExecutionSnapshot;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Feeds scans through the existing serial queue; product locks and indexes remain the final fence. */
@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ScanDriver {
    private static final Set<ReviewJobStatus> ACTIVE = Set.of(ReviewJobStatus.QUEUED, ReviewJobStatus.RUNNING);
    private final ReviewScanStore scans;
    private final ReviewService reviews;
    private final ScanService inventory;
    private final ScanBudget budget;
    private final ReviewQueue queue;
    private final ReviewJobRepository jobs;
    private final ReviewJobLifecycleService lifecycle;
    private final TransactionTemplate transactions;
    private final ScanProperties limits;
    private final Clock clock;

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "practice-scan-driver", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1S")
    public void tick() {
        boolean enumerated = false;
        for (var candidate : scans.active(20)) {
            if (!candidate.inventoryRecorded()) {
                if (enumerated) continue;
                enumerated = true;
            }
            try {
                transactions.executeWithoutResult(status -> advance(scans.lock(
                        candidate.review().workspaceId(), candidate.review().id())));
            } catch (ScanService.EnumerationFailure failure) {
                closeAfterFailure(candidate, failure.reason());
            } catch (RuntimeException failure) {
                closeAfterFailure(candidate, ReviewStopReason.ERROR);
            }
        }
    }

    public void advance(ScanExecutionSnapshot state) {
        var review = state.review();
        var active = jobs.findByReviewIdAndWorkspaceIdAndStatusIn(review.id(), review.workspaceId(), ACTIVE);
        if (active.isPresent()) {
            if (state.cancellationRequested()
                    || !clock.instant().isBefore(state.createdAt().plus(limits.scanDeadline())))
                lifecycle.cancel(review.workspaceId(), active.get().getId());
            return;
        }
        if (jobs.hasUnsettledInvocation(review.workspaceId(), review.id())) return;
        if (state.cancellationRequested()) {
            close(state, ReviewStatus.CANCELLED, ReviewStopReason.CANCELLED);
            return;
        }
        if (!clock.instant().isBefore(state.createdAt().plus(limits.scanDeadline()))) {
            close(state, ReviewStatus.PARTIAL, ReviewStopReason.DEADLINE);
            return;
        }
        if (!state.inventoryRecorded()) {
            if (!reviews.start(review.workspaceId(), review.id())) return;
            inventory.enumerate(review);
            state = scans.lock(review.workspaceId(), review.id());
        }
        if (!state.inventoryComplete()
                || state.listedPracticeIds().size() != review.practices().size())
            throw new ScanService.EnumerationFailure(ReviewStopReason.ENUMERATION_FAILED);
        var pending = scans.pending(review.workspaceId(), review.id(), 1000);
        for (var assignment : pending) scans.reuse(review.workspaceId(), assignment.id());
        pending = scans.pending(review.workspaceId(), review.id(), 1000);
        if (pending.isEmpty()) {
            state = scans.lock(review.workspaceId(), review.id());
            boolean complete = state.judged() + state.reused() == state.listed() && state.withheld() == 0;
            close(
                    state,
                    complete ? ReviewStatus.COMPLETE : ReviewStatus.PARTIAL,
                    complete
                            ? null
                            : state.withheld() > 0
                                    ? ReviewStopReason.RESULT_WITHHELD
                                    : ReviewStopReason.EXECUTION_INCOMPLETE);
            return;
        }
        long item = pending.getFirst().item().itemId();
        var selected = pending.stream()
                .filter(assignment -> assignment.item().itemId() == item)
                .toList();
        boolean permits;
        try {
            permits = budget.permits(state, selected);
        } catch (ReviewJobStateConflictException changed) {
            close(state, ReviewStatus.PARTIAL, ReviewStopReason.MODEL_CHANGED);
            return;
        } catch (IllegalStateException unavailable) {
            close(state, ReviewStatus.PARTIAL, ReviewStopReason.MODEL_UNAVAILABLE);
            return;
        }
        if (!permits) {
            close(state, ReviewStatus.PARTIAL, ReviewStopReason.BUDGET);
            return;
        }
        try {
            queue.enqueue(
                    review,
                    item,
                    selected.stream().map(assignment -> assignment.id()).toList());
        } catch (ReviewJobStateConflictException changed) {
            close(state, ReviewStatus.PARTIAL, ReviewStopReason.MODEL_CHANGED);
        }
    }

    private void closeAfterFailure(ScanExecutionSnapshot candidate, ReviewStopReason reason) {
        transactions.executeWithoutResult(status -> {
            var state = scans.lock(
                    candidate.review().workspaceId(), candidate.review().id());
            if (jobs.findByReviewIdAndWorkspaceIdAndStatusIn(
                            state.review().id(), state.review().workspaceId(), ACTIVE)
                    .isEmpty())
                close(state, state.inventoryRecorded() ? ReviewStatus.PARTIAL : ReviewStatus.FAILED, reason);
        });
    }

    private void close(ScanExecutionSnapshot state, ReviewStatus status, @Nullable ReviewStopReason reason) {
        var now = clock.instant();
        reviews.complete(
                state.review().workspaceId(),
                state.review().id(),
                new ReviewCompletion(status, reason, now, state.createdAt()));
    }
}
