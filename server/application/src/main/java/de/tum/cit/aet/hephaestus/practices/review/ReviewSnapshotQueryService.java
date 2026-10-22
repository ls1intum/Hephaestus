package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshotQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@Primary
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ReviewSnapshotQueryService implements ReviewSnapshotQuery {
    private final ReviewRepository reviews;
    private final ReviewPracticeRepository assignments;
    private final PracticeVersionRepository versions;
    private final ProductItemQuery items;
    private final ProductItemRepository itemRows;
    private final JsonMapper json;

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> publicReviewedAt(long workspaceId, UUID reviewId) {
        return reviews.findByIdAndWorkspaceId(reviewId, workspaceId)
                .flatMap(review -> Optional.ofNullable(review.getPublicReviewAt()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BigDecimal> budgetUsd(long workspaceId, UUID reviewId) {
        return reviews.findByIdAndWorkspaceId(reviewId, workspaceId)
                .flatMap(review -> Optional.ofNullable(review.getBudgetUsd()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReviewSnapshot> find(long workspaceId, UUID reviewId) {
        return reviews.findByIdAndWorkspaceId(reviewId, workspaceId).flatMap(review -> {
            var ref = items.find(workspaceId, review.getItemId());
            if (ref.isEmpty()) return Optional.empty();
            var pinned = new ArrayList<PinnedPractice>();
            for (var assignment : assignments.findByWorkspaceIdAndReviewIdOrderByPracticeIdAsc(workspaceId, reviewId)) {
                if (assignment.getPracticeVersionId() == null || assignment.getMethod() == null) continue;
                var version = versions.findByIdAndWorkspaceId(
                                assignment.getPracticeVersionId(), assignment.getWorkspaceId())
                        .filter(v -> v.getWorkspaceId() == workspaceId
                                && v.getPractice().getId().equals(assignment.getPracticeId()));
                if (version.isPresent()) {
                    var v = version.orElseThrow();
                    pinned.add(new PinnedPractice(
                            assignment.getPracticeId(),
                            assignment.getPracticeVersionId(),
                            json.convertValue(v.getDefinition(), PracticeDefinition.class),
                            v.getReviewDigest(),
                            assignment.getMethod()));
                }
            }
            var eventId = review.getOrigin() == ReviewOrigin.EVENT ? review.getEventId() : null;
            return Optional.of(new ReviewSnapshot(
                    reviewId,
                    workspaceId,
                    ref.orElseThrow(),
                    review.getKind(),
                    review.getItemVersion() == null
                            ? null
                            : new ItemVersion(review.getItemVersion(), review.getBaseVersion()),
                    review.getOrigin(),
                    review.getRolloutRevision(),
                    pinned,
                    eventId,
                    eventId == null
                            ? null
                            : itemRows.event(workspaceId, review.getItemId(), eventId)
                                    .getEvidenceExpiresAt()));
        });
    }
}
