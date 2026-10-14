package de.tum.cit.aet.hephaestus.practices.review.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeReviewReadiness;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchPauseReason;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBudget;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ReviewBatchDriverTest extends BaseUnitTest {
    final ReviewBatchRepository batches = mock(ReviewBatchRepository.class);
    final ReviewBatchScopeRepository scope = mock(ReviewBatchScopeRepository.class);
    final ReviewBatchSubmitter submitter = mock(ReviewBatchSubmitter.class);
    final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    final ReviewBudget budget = mock(ReviewBudget.class);
    final ReviewBatchSpend spend = mock(ReviewBatchSpend.class);
    final ReviewRepository reviews = mock(ReviewRepository.class);
    final PracticeReviewReadiness readiness = mock(PracticeReviewReadiness.class);
    final Instant now = Instant.parse("2026-10-14T00:00:00Z");
    final ReviewBatchDriver driver = new ReviewBatchDriver(
            batches,
            scope,
            submitter,
            workspaces,
            budget,
            readiness,
            new ReviewBatchProperties(2, Duration.ofDays(400), 100, Duration.ofDays(90)),
            Clock.fixed(now, ZoneOffset.UTC),
            reviews,
            spend);
    ReviewBatch batch;
    Workspace workspace;

    @BeforeEach
    void setUp() {
        batch = ReviewBatch.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .kind("scm.pull_request")
                .origin(ReviewBatchOrigin.BACKFILL)
                .status(ReviewBatchStatus.RUNNING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        workspace = new Workspace();
        workspace.setId(1L);
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        lenient().when(spend.remaining(batch)).thenReturn(new ReviewBatchSpend.Snapshot(null, true));
        lenient().when(workspaces.findById(1L)).thenReturn(Optional.of(workspace));
        lenient().when(readiness.hasRunnableAgent(1L)).thenReturn(true);
        lenient().when(budget.canRequest(1, ReviewKind.ITEM, null)).thenReturn(true);
    }

    @Test
    void exhaustedBudgetPausesWithoutAdvancingCursor() {
        when(budget.canRequest(1, ReviewKind.ITEM, null)).thenReturn(false);
        driver.advance(batch);
        assertThat(batch.getStatus()).isEqualTo(ReviewBatchStatus.PAUSED);
        assertThat(batch.getPauseReason()).isEqualTo(ReviewBatchPauseReason.BUDGET_EXHAUSTED);
        assertThat(batch.getCursorItemId()).isNull();
        verifyNoInteractions(submitter);
    }

    @Test
    void resumedBudgetContinuesFromTheStoredCursor() {
        batch.setStatus(ReviewBatchStatus.RUNNING);
        batch.setPauseReason(null);
        batch.setCursorItemId(5L);
        when(scope.next(1, batch.getId(), 5L, 2)).thenReturn(List.of(6L));
        when(submitter.offer(batch, 6)).thenReturn(ReviewBatchSubmitter.SubmissionOutcome.SUBMITTED);
        driver.advance(batch);
        assertThat(batch.getStatus()).isEqualTo(ReviewBatchStatus.RUNNING);
        assertThat(batch.getPauseReason()).isNull();
        assertThat(batch.getCursorItemId()).isEqualTo(6L);
        assertThat(batch.getSubmittedCount()).isOne();
    }

    @Test
    void oneFailedItemDoesNotStopTheNextTarget() {
        when(scope.next(1, batch.getId(), null, 2)).thenReturn(List.of(1L, 2L));
        when(submitter.offer(batch, 1)).thenThrow(new IllegalStateException("unavailable"));
        when(submitter.offer(batch, 2)).thenReturn(ReviewBatchSubmitter.SubmissionOutcome.SUBMITTED);
        driver.advance(batch);
        assertThat(batch.getFailedCount()).isOne();
        assertThat(batch.getSubmittedCount()).isOne();
        assertThat(batch.getCursorItemId()).isEqualTo(2L);
    }

    @Test
    void emptyFrozenScopeCompletesBatch() {
        when(scope.next(1, batch.getId(), null, 2)).thenReturn(List.of());
        driver.advance(batch);
        assertThat(batch.getStatus()).isEqualTo(ReviewBatchStatus.COMPLETED);
        assertThat(batch.getFinishedAt()).isEqualTo(now);
    }

    @Test
    void cancelledBatchDoesNotOfferMoreWork() {
        batch.setStatus(ReviewBatchStatus.CANCELLED);
        driver.advance(batch);
        verifyNoInteractions(submitter, scope);
    }

    @Test
    void missingModelHasItsOwnPauseReason() {
        when(readiness.hasRunnableAgent(1L)).thenReturn(false);
        driver.advance(batch);
        assertThat(batch.getPauseReason()).isEqualTo(ReviewBatchPauseReason.REVIEW_MODEL_UNBOUND);
    }

    @Test
    void missingWorkspaceHasItsOwnPauseReason() {
        when(workspaces.findById(1L)).thenReturn(Optional.empty());
        driver.advance(batch);
        assertThat(batch.getPauseReason()).isEqualTo(ReviewBatchPauseReason.WORKSPACE_UNAVAILABLE);
    }

    @Test
    void competingScanKeepsTheTargetAndDoesNotRecordAFailure() {
        batch.setKind("scm.repository");
        when(budget.canRequest(1, ReviewKind.SCAN, null)).thenReturn(true);
        when(reviews.existsByWorkspaceIdAndKindAndStatusIn(
                        ArgumentMatchers.eq(1L),
                        ArgumentMatchers.eq(ReviewKind.SCAN),
                        ArgumentMatchers.anyCollection()))
                .thenReturn(false, false, true);
        when(scope.next(1, batch.getId(), null, 1)).thenReturn(List.of(6L));
        when(submitter.offer(batch, 6))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "A scan is already active"));
        driver.advance(batch);
        assertThat(batch.getCursorItemId()).isNull();
        assertThat(batch.getFailedCount()).isZero();
        assertThat(batch.getSubmittedCount()).isZero();
        assertThat(batch.getStatus()).isEqualTo(ReviewBatchStatus.RUNNING);
    }
}
