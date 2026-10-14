package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemQuery;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchPauseReason;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequest;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequestRejected;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewBatchSubmitter {
    public sealed interface SubmissionOutcome {
        record Submitted() implements SubmissionOutcome {}

        record Skipped() implements SubmissionOutcome {}

        record Deferred() implements SubmissionOutcome {}

        record Paused(ReviewBatchPauseReason reason) implements SubmissionOutcome {}
    }

    private final ProductItemQuery items;
    private final ReviewService reviews;
    private final ReviewRepository ledger;
    private final ReviewBatchSpend spend;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SubmissionOutcome offer(ReviewBatch batch, long itemId) {
        var item = items.find(batch.getWorkspaceId(), itemId);
        if (item.isEmpty()) return new SubmissionOutcome.Skipped();
        ReviewKind kind = items.current(item.get())
                .map(s -> s.item().kind().value().equals("scm.repository") ? ReviewKind.SCAN : ReviewKind.ITEM)
                .orElseThrow();
        if (ledger.existsByWorkspaceIdAndBatchIdAndItemIdAndStatusNot(
                batch.getWorkspaceId(), batch.getId(), itemId, ReviewStatus.CANCELLED))
            return new SubmissionOutcome.Submitted();
        var remaining = spend.remaining(batch);
        try {
            reviews.request(new ReviewRequest(
                    batch.getWorkspaceId(),
                    itemId,
                    kind,
                    null,
                    ReviewBatchOrigins.origin(batch),
                    null,
                    null,
                    batch.getId(),
                    remaining.remainingUsd(),
                    null,
                    null,
                    List.of(),
                    true));
        } catch (ReviewRequestRejected refusal) {
            return switch (refusal.reason()) {
                case BUDGET_UNAVAILABLE -> new SubmissionOutcome.Paused(ReviewBatchPauseReason.BUDGET_EXHAUSTED);
                case WORKSPACE_INACTIVE -> new SubmissionOutcome.Paused(ReviewBatchPauseReason.WORKSPACE_UNAVAILABLE);
                case MODEL_UNAVAILABLE -> new SubmissionOutcome.Paused(ReviewBatchPauseReason.REVIEW_MODEL_UNBOUND);
                case SCAN_ACTIVE, SOURCE_UNAVAILABLE -> new SubmissionOutcome.Deferred();
                case ITEM_INELIGIBLE, REQUEST_CONFLICT -> new SubmissionOutcome.Skipped();
            };
        }
        return new SubmissionOutcome.Submitted();
    }
}
