package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.review.ProductItemQuery;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.review.report.ProblemComparison;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequest;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewService;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewBatchSubmitter {
    public enum SubmissionOutcome {
        SUBMITTED,
        SKIPPED
    }

    private final ProductItemQuery items;
    private final ReviewService reviews;
    private final ReviewRepository ledger;
    private final ReviewBatchSpend spend;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SubmissionOutcome offer(ReviewBatch batch, long itemId) {
        var item = items.find(batch.getWorkspaceId(), itemId);
        if (item.isEmpty()) return SubmissionOutcome.SKIPPED;
        ReviewKind kind = items.current(item.get())
                .map(s -> s.item().kind().value().equals("scm.repository") ? ReviewKind.SCAN : ReviewKind.ITEM)
                .orElseThrow();
        if (ledger.findByWorkspaceIdAndBatchIdOrderByCreatedAtAsc(batch.getWorkspaceId(), batch.getId()).stream()
                .anyMatch(r -> r.getItemId() == itemId && r.getStatus() != ReviewStatus.CANCELLED))
            return SubmissionOutcome.SUBMITTED;
        var requestKey = kind == ReviewKind.SCAN ? UUID.randomUUID() : null;
        var requestDigest = kind == ReviewKind.SCAN
                ? ProblemComparison.sha256(batch.getId() + "/" + itemId + "/" + batch.getBudgetUsd())
                : null;
        var remaining = spend.remaining(batch);
        if (remaining.exhausted()) throw new IllegalStateException("The batch budget is unavailable.");
        var id = reviews.request(new ReviewRequest(
                batch.getWorkspaceId(),
                itemId,
                kind,
                null,
                ReviewBatchOrigins.origin(batch),
                null,
                null,
                batch.getId(),
                remaining.remainingUsd(),
                requestKey,
                requestDigest,
                List.of(),
                true));
        if (kind == ReviewKind.SCAN) {
            var review =
                    ledger.findByIdAndWorkspaceId(id, batch.getWorkspaceId()).orElseThrow();
            var snapshot = items.pin(item.orElseThrow(), null);
            var ref = snapshot.state().get("defaultBranch");
            if (ref == null || ref.isBlank())
                throw new IllegalStateException("The repository default branch is unavailable.");
            var checks = (ObjectNode) review.getChecks().deepCopy();
            checks.put("ref", ref);
            review.setChecks(checks);
        }
        return SubmissionOutcome.SUBMITTED;
    }
}
