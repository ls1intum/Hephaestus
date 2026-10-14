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
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class ReviewAssignmentQueryService implements ReviewAssignmentQuery {
    private final ReviewRepository reviews;
    private final ReviewPracticeRepository assignments;
    private final ReviewItemRepository items;
    private final PracticeRepository practices;
    private final PracticeVersionRepository versions;
    private final ProductItemQuery itemQuery;
    private final JsonMapper json;

    @Override
    @Transactional(readOnly = true)
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
