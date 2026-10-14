package de.tum.cit.aet.hephaestus.practices.review.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemLabel;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemSnapshot;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemQuery;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequest;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

class ReviewBatchSubmitterTest extends BaseUnitTest {
    final ProductItemQuery items = mock(ProductItemQuery.class);
    final ReviewService reviews = mock(ReviewService.class);
    final ReviewBatchSpend spend = mock(ReviewBatchSpend.class);
    final ReviewRepository ledger = mock(ReviewRepository.class);
    final ReviewBatchSubmitter submitter = new ReviewBatchSubmitter(items, reviews, ledger, spend);

    @Test
    void missingItemIsSkipped() {
        var batch = batch();
        when(items.find(1, 7)).thenReturn(Optional.empty());
        assertThat(submitter.offer(batch, 7)).isEqualTo(ReviewBatchSubmitter.SubmissionOutcome.SKIPPED);
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
        var snapshot = new ItemSnapshot(
                ref,
                new ItemVersion("v1", null),
                new IntegrationRef(IntegrationKind.GITHUB, 1, "test", null),
                Map.of("defaultBranch", "main"),
                List.of(),
                new ItemLabel(7, "Item", "Item", 1, "Container", null, IntegrationKind.GITHUB));
        when(items.current(ref)).thenReturn(Optional.of(snapshot));
        when(spend.remaining(batch)).thenReturn(new ReviewBatchSpend.Snapshot(reserved, true));
        var id = UUID.randomUUID();
        when(reviews.request(ArgumentMatchers.any())).thenReturn(id);
        if (reviewKind == ReviewKind.SCAN) {
            when(items.pin(ref, null)).thenReturn(snapshot);
            when(ledger.findByIdAndWorkspaceId(id, 1))
                    .thenReturn(Optional.of(Review.builder().id(id).build()));
        }
        assertThat(submitter.offer(batch, 7)).isEqualTo(ReviewBatchSubmitter.SubmissionOutcome.SUBMITTED);
        var capture = ArgumentCaptor.forClass(ReviewRequest.class);
        verify(reviews).request(capture.capture());
        var request = capture.getValue();
        assertThat(request.kind()).isEqualTo(reviewKind);
        assertThat(request.origin()).isEqualTo(expected);
        assertThat(request.eventId()).isNull();
        assertThat(request.batchId()).isEqualTo(batch.getId());
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
}
