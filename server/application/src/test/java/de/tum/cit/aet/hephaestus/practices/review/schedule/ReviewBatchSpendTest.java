package de.tum.cit.aet.hephaestus.practices.review.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewUsageSnapshot;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewBatchSpendTest extends BaseUnitTest {
    final ReviewRepository reviews = mock(ReviewRepository.class);
    final ReviewRuntimeQuery runtime = mock(ReviewRuntimeQuery.class);
    final ReviewBatchSpend spend = new ReviewBatchSpend(reviews, runtime);

    @Test
    void nextScanReceivesOnlyTheCumulativeRemainingBudget() {
        var batch = ReviewBatch.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .budgetUsd(new BigDecimal("10"))
                .build();
        var first = Review.builder().id(UUID.randomUUID()).build();
        var second = Review.builder().id(UUID.randomUUID()).build();
        when(reviews.findByWorkspaceIdAndBatchIdOrderByCreatedAtAsc(1, batch.getId()))
                .thenReturn(List.of(first, second));
        when(runtime.usage(1, first.getId())).thenReturn(usage("3.25", true));
        when(runtime.usage(1, second.getId())).thenReturn(usage("2.10001", true));
        assertThat(spend.remaining(batch).remainingUsd()).isEqualByComparingTo("4.6499");
    }

    @Test
    void unpricedLedgerDoesNotBecomeZeroSpend() {
        var batch = ReviewBatch.builder()
                .id(UUID.randomUUID())
                .workspaceId(1)
                .budgetUsd(BigDecimal.TEN)
                .build();
        var review = Review.builder().id(UUID.randomUUID()).build();
        when(reviews.findByWorkspaceIdAndBatchIdOrderByCreatedAtAsc(1, batch.getId()))
                .thenReturn(List.of(review));
        when(runtime.usage(1, review.getId())).thenReturn(new ReviewUsageSnapshot(null, false, 1, 100, 100, List.of()));
        assertThat(spend.remaining(batch).verified()).isFalse();
        assertThat(spend.remaining(batch).exhausted()).isTrue();
    }

    @Test
    void datedWorkHasNoInventedScanBudget() {
        assertThat(spend.remaining(ReviewBatch.builder().build())).isEqualTo(new ReviewBatchSpend.Snapshot(null, true));
        verifyNoInteractions(runtime, reviews);
    }

    ReviewUsageSnapshot usage(String cost, boolean priced) {
        return new ReviewUsageSnapshot(new BigDecimal(cost), priced, 1, 100, 100, List.of());
    }
}
