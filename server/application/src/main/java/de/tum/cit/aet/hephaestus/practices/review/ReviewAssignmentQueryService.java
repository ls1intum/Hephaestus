package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemRef;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewItem;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewAssignmentQueryService implements ReviewAssignmentQuery {
    private final ReviewRepository reviews;
    private final ReviewPracticeRepository assignments;
    private final ReviewItemRepository items;
    private final PracticeRepository practices;
    private final PracticeVersionRepository versions;
    private final ProductItemQuery itemQuery;
    private final JsonMapper json;

    @Override
    public List<ReviewAssignmentId> executable(long workspaceId, UUID reviewId, long itemId) {
        return items.executablePracticeIds(workspaceId, reviewId, itemId).stream()
                .map(practiceId -> new ReviewAssignmentId(reviewId, practiceId, itemId))
                .filter(id -> find(workspaceId, id).isPresent())
                .toList();
    }

    @Override
    public Optional<ReviewAssignmentSnapshot> find(long workspaceId, ReviewAssignmentId id) {
        var review = reviews.findByIdAndWorkspaceId(id.reviewId(), workspaceId);
        var assignment =
                assignments.findByWorkspaceIdAndReviewIdAndPracticeId(workspaceId, id.reviewId(), id.practiceId());
        var item = items.findByWorkspaceIdAndReviewIdAndPracticeIdAndItemId(
                workspaceId, id.reviewId(), id.practiceId(), id.itemId());
        if (review.isEmpty() || assignment.isEmpty() || item.isEmpty()) return Optional.empty();
        var pinned = assignment.orElseThrow();
        var member = item.orElseThrow();
        var parent = review.orElseThrow();
        if (pinned.getPracticeVersionId() == null || pinned.getMethod() == null || member.getItemVersion() == null)
            return Optional.empty();
        var version = versions.findByIdAndWorkspaceId(pinned.getPracticeVersionId(), workspaceId)
                .filter(v -> v.getWorkspaceId() == workspaceId
                        && v.getPractice().getId().equals(id.practiceId()));
        var practice = practices.findByIdAndWorkspaceId(id.practiceId(), workspaceId);
        var ref = itemQuery.find(workspaceId, id.itemId());
        if (version.isEmpty() || practice.isEmpty() || ref.isEmpty()) return Optional.empty();
        if (!version.orElseThrow().getDefinition().has("about")) return Optional.empty();
        var definition = json.convertValue(version.orElseThrow().getDefinition(), PracticeDefinition.class);
        var detail = member.getDetail();
        if (parent.getKind() == ReviewKind.ITEM && (detail == null || !detail.has("developerId")))
            return Optional.empty();
        Long developer = parent.getKind() == ReviewKind.SCAN
                        || detail == null
                        || detail.path("developerId").isNull()
                ? null
                : detail.path("developerId").longValue();
        if (parent.getKind() == ReviewKind.ITEM && definition.about() != null && developer == null)
            return Optional.empty();
        return Optional.of(new ReviewAssignmentSnapshot(
                id,
                ref.orElseThrow(),
                new ItemVersion(
                        member.getItemVersion(),
                        parent.getKind() == ReviewKind.SCAN ? parent.getItemVersion() : parent.getBaseVersion()),
                developer,
                practice.orElseThrow().getSlug(),
                new PinnedPractice(
                        id.practiceId(),
                        pinned.getPracticeVersionId(),
                        definition,
                        version.orElseThrow().getReviewDigest(),
                        pinned.getMethod())));
    }

    /**
     * Reads only the review, its rows and the workspace's items: the attribution is the developer the review wrote on
     * its row when it listed the item, never the job's own input. Recorded keys are read in one scoped query and each
     * item once. The selection is all or nothing: a key of another review, a recorded key without its row, or any
     * selected row without a readable item or attribution leaves the whole run unattributed.
     */
    @Override
    public List<Retained> retained(long workspaceId, UUID reviewId, UUID jobId, Collection<ReviewAssignmentId> ids) {
        if (ids.stream().anyMatch(id -> !id.reviewId().equals(reviewId))) return List.of();
        var review = reviews.findByIdAndWorkspaceId(reviewId, workspaceId);
        if (review.isEmpty()) return List.of();
        List<ReviewItem> rows;
        if (ids.isEmpty()) {
            rows = items.findByWorkspaceIdAndReviewIdAndJobIdOrderByPracticeIdAscItemIdAsc(
                    workspaceId, reviewId, jobId);
        } else {
            var wanted = Set.copyOf(ids);
            var itemIds = ids.stream().map(ReviewAssignmentId::itemId).collect(Collectors.toSet());
            rows = items.findByWorkspaceIdAndReviewIdAndItemIdIn(workspaceId, reviewId, itemIds).stream()
                    .filter(row ->
                            wanted.contains(new ReviewAssignmentId(reviewId, row.getPracticeId(), row.getItemId())))
                    .sorted(Comparator.comparingLong(ReviewItem::getPracticeId)
                            .thenComparingLong(ReviewItem::getItemId))
                    .toList();
            if (rows.size() != wanted.size()) return List.of();
        }
        boolean scan = review.orElseThrow().getKind() == ReviewKind.SCAN;
        Map<Long, Optional<ItemRef>> refs = new HashMap<>();
        var retained = new ArrayList<Retained>();
        for (var row : rows) {
            var ref = refs.computeIfAbsent(row.getItemId(), itemId -> itemQuery.find(workspaceId, itemId));
            if (ref.isEmpty()) return List.of();
            Long developer = null;
            if (!scan) {
                var detail = row.getDetail();
                if (detail == null || !detail.has("developerId")) return List.of();
                var named = detail.path("developerId");
                if (!named.isNull()) {
                    if (!named.isIntegralNumber() || !named.canConvertToLong()) return List.of();
                    developer = named.longValue();
                }
            }
            retained.add(new Retained(
                    new ReviewAssignmentId(reviewId, row.getPracticeId(), row.getItemId()),
                    ref.orElseThrow(),
                    developer));
        }
        return List.copyOf(retained);
    }
}
