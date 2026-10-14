package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemVersion;
import de.tum.cit.aet.hephaestus.practices.PracticeDefinition;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.spi.PinnedPractice;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
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
    private final JdbcClient jdbc;

    @Override
    public List<ReviewAssignmentId> executable(long workspaceId, UUID reviewId, long itemId) {
        return jdbc
                .sql("SELECT m.practice_id FROM review_item m JOIN review r ON r.id=m.review_id AND"
                        + " r.workspace_id=m.workspace_id JOIN review_practice p ON p.review_id=m.review_id AND"
                        + " p.workspace_id=m.workspace_id AND p.practice_id=m.practice_id WHERE m.workspace_id=:ws"
                        + " AND m.review_id=:review AND m.item_id=:item AND m.state='LISTED' AND p.status='READY'"
                        + " AND r.status IN ('QUEUED','RUNNING') AND NOT"
                        + " COALESCE((r.checks->>'cancellationRequested')::boolean,false) ORDER BY m.practice_id")
                .param("ws", workspaceId)
                .param("review", reviewId)
                .param("item", itemId)
                .query((rs, n) -> new ReviewAssignmentId(reviewId, rs.getLong(1), itemId))
                .list()
                .stream()
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
        var version = versions.findById(pinned.getPracticeVersionId())
                .filter(v -> v.getWorkspaceId() == workspaceId
                        && v.getPractice().getId().equals(id.practiceId()));
        var practice = practices
                .findById(id.practiceId())
                .filter(p -> p.getWorkspace().getId().equals(workspaceId));
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
}
