package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeReviewReadiness;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchPauseReason;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBudget;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
@Slf4j
@WorkspaceAgnostic("Selects active batches across workspaces; each mutation retains its workspace")
public class ReviewBatchDriver {
    private final ReviewBatchRepository batches;
    private final ReviewBatchScopeQuery scope;
    private final ReviewBatchSubmitter submitter;
    private final WorkspaceRepository workspaces;
    private final ReviewBudget budget;
    private final PracticeReviewReadiness readiness;
    private final ReviewBatchProperties properties;
    private final Clock clock;
    private final ReviewRepository reviews;
    private final ReviewBatchSpend spend;

    @Scheduled(fixedDelay = 120000, initialDelay = 120000)
    @SchedulerLock(name = "review-batch-driver", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void tick() {
        for (var batch : batches.findByStatusIn(
                List.of(ReviewBatchStatus.RUNNING, ReviewBatchStatus.PAUSED), PageRequest.ofSize(5))) {
            try {
                advance(batch);
            } catch (RuntimeException failure) {
                log.warn(
                        "Could not advance review batch {} in workspace {}",
                        batch.getId(),
                        batch.getWorkspaceId(),
                        failure);
            }
        }
    }

    void advance(ReviewBatch batch) {
        if (batch.getStatus() != ReviewBatchStatus.RUNNING) return;
        var workspace = workspaces.findById(batch.getWorkspaceId()).orElse(null);
        boolean scan = batch.getKind().equals("scm.repository");
        if (scan
                && workspace != null
                && workspace.getStatus() == Workspace.WorkspaceStatus.ACTIVE
                && reviews.existsByWorkspaceIdAndKindAndStatusIn(
                        batch.getWorkspaceId(), ReviewKind.SCAN, List.of(ReviewStatus.QUEUED, ReviewStatus.RUNNING)))
            return;
        var remaining = spend.remaining(batch);
        ReviewBatchPauseReason reason = workspace == null || workspace.getStatus() != Workspace.WorkspaceStatus.ACTIVE
                ? ReviewBatchPauseReason.WORKSPACE_UNAVAILABLE
                : !readiness.hasRunnableAgent(batch.getWorkspaceId())
                        ? ReviewBatchPauseReason.REVIEW_MODEL_UNBOUND
                        : remaining.exhausted()
                                        || !budget.canRequest(
                                                batch.getWorkspaceId(),
                                                batch.getKind().equals("scm.repository")
                                                        ? ReviewKind.SCAN
                                                        : ReviewKind.ITEM,
                                                remaining.remainingUsd())
                                ? ReviewBatchPauseReason.BUDGET_EXHAUSTED
                                : null;
        if (reason != null) {
            batch.setStatus(ReviewBatchStatus.PAUSED);
            batch.setPauseReason(reason);
            batches.save(batch);
            return;
        }
        batch.setStatus(ReviewBatchStatus.RUNNING);
        batch.setPauseReason(null);

        if (scan
                && reviews.existsByWorkspaceIdAndKindAndStatusIn(
                        batch.getWorkspaceId(), ReviewKind.SCAN, List.of(ReviewStatus.QUEUED, ReviewStatus.RUNNING)))
            return;
        var ids = scope.next(
                batch.getWorkspaceId(), batch.getId(), batch.getCursorItemId(), scan ? 1 : properties.batchSize());
        if (ids.isEmpty()) {
            batch.setStatus(ReviewBatchStatus.COMPLETED);
            batch.setFinishedAt(clock.instant());
            batches.save(batch);
            return;
        }
        for (long itemId : ids) {
            try {
                if (submitter.offer(batch, itemId) == ReviewBatchSubmitter.SubmissionOutcome.SUBMITTED)
                    batch.setSubmittedCount(batch.getSubmittedCount() + 1);
                else batch.setSkippedCount(batch.getSkippedCount() + 1);
            } catch (ResponseStatusException failure) {
                if (scan
                        && failure.getStatusCode().equals(HttpStatus.CONFLICT)
                        && reviews.existsByWorkspaceIdAndKindAndStatusIn(
                                batch.getWorkspaceId(),
                                ReviewKind.SCAN,
                                List.of(ReviewStatus.QUEUED, ReviewStatus.RUNNING))) {
                    batch.setUpdatedAt(clock.instant());
                    batches.save(batch);
                    return;
                }
                log.warn("Could not request review for item {} in batch {}", itemId, batch.getId(), failure);
                batch.setFailedCount(batch.getFailedCount() + 1);
            } catch (RuntimeException failure) {
                log.warn("Could not request review for item {} in batch {}", itemId, batch.getId(), failure);
                batch.setFailedCount(batch.getFailedCount() + 1);
            }
            batch.setCursorItemId(itemId);
        }
        batch.setUpdatedAt(clock.instant());
        batches.save(batch);
    }
}
