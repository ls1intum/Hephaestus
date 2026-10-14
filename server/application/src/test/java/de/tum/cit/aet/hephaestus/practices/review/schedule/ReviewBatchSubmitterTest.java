package de.tum.cit.aet.hephaestus.practices.review.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemQuery;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRequestRejected;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequest;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class ReviewBatchSubmitterTest extends BaseUnitTest {
    final ProductItemQuery items = mock(ProductItemQuery.class);
    final ReviewService reviews = mock(ReviewService.class);
    final ReviewRepository ledger = mock(ReviewRepository.class);
    final ReviewBatchSubmitter submitter = new ReviewBatchSubmitter(items, reviews, ledger);

    @Test
    void missingItemIsSkipped() {
        var batch = batch();
        when(items.find(1, 7)).thenReturn(Optional.empty());
        assertThat(submitter.offer(batch, 7, null)).isEqualTo(new ReviewBatchSubmitter.SubmissionOutcome.Skipped());
        verifyNoInteractions(reviews);
    }

    @Test
    void datedWorkUsesBackfillOriginWithoutASyntheticEvent() {
        assertRequest("scm.pull_request", ReviewBatchOrigin.BACKFILL, ReviewKind.ITEM, ReviewOrigin.BACKFILL, null);
    }

    @Test
    void repositoryUsesScanAndTheReservedScheduleBudget() {
        assertRequest(
                "scm.repository",
                ReviewBatchOrigin.SCHEDULE,
                ReviewKind.SCAN,
                ReviewOrigin.SCHEDULE,
                new BigDecimal("2.0000"));
    }

    void assertRequest(
            String kind,
            ReviewBatchOrigin origin,
            ReviewKind reviewKind,
            ReviewOrigin expected,
            @Nullable BigDecimal reserved) {
        var batch = batch();
        batch.setOrigin(origin);
        batch.setBudgetUsd(reserved);
        var ref = new ItemRef(1, 7, new ItemKind(kind), 7L, null, null);
        when(items.find(1, 7)).thenReturn(Optional.of(ref));
        var id = UUID.randomUUID();
        when(reviews.request(ArgumentMatchers.any())).thenReturn(id);
        assertThat(submitter.offer(batch, 7, reserved))
                .isEqualTo(new ReviewBatchSubmitter.SubmissionOutcome.Submitted());
        var capture = ArgumentCaptor.forClass(ReviewRequest.class);
        verify(reviews).request(capture.capture());
        var request = capture.getValue();
        assertThat(request.kind()).isEqualTo(reviewKind);
        assertThat(request.origin()).isEqualTo(expected);
        assertThat(request.eventId()).isNull();
        assertThat(request.batchId()).isEqualTo(batch.getId());
        assertThat(request.requestKey()).isNull();
        assertThat(request.requestDigest()).isNull();
        if (reserved == null) assertThat(request.budgetUsd()).isNull();
        else assertThat(request.budgetUsd()).isEqualByComparingTo(reserved);
    }

    ReviewBatch batch() {
        return ReviewBatch.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .kind("scm.pull_request")
                .origin(ReviewBatchOrigin.BACKFILL)
                .build();
    }

    @Test
    void shouldSkipAnUnavailableSourceForTheNextBatch() {
        assertThat(refusal(ReviewRequestRejected.Reason.SOURCE_UNAVAILABLE))
                .isEqualTo(new ReviewBatchSubmitter.SubmissionOutcome.Skipped());
    }

    @Test
    void shouldSkipAPermanentRequestConflictWithoutReportingACaptureFailure() {
        assertThat(refusal(ReviewRequestRejected.Reason.REQUEST_CONFLICT))
                .isEqualTo(new ReviewBatchSubmitter.SubmissionOutcome.Skipped());
    }

    private ReviewBatchSubmitter.SubmissionOutcome refusal(ReviewRequestRejected.Reason reason) {
        var batch = batch();
        var ref = new ItemRef(1, 7, new ItemKind("scm.pull_request"), 7L, null, null);
        when(items.find(1, 7)).thenReturn(Optional.of(ref));
        when(reviews.request(ArgumentMatchers.any())).thenThrow(new ReviewRequestRejected(reason, "Unavailable"));
        return submitter.offer(batch, 7, null);
    }
}
