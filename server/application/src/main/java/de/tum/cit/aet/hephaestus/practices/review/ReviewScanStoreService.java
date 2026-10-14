package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemWriter;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewItem;
import de.tum.cit.aet.hephaestus.practices.spi.RemovalGuardState;
import de.tum.cit.aet.hephaestus.practices.spi.ResultEndReason;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentId;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewAssignmentSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewItemState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewPracticeStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewScanStore;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshotQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ScanExecutionSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ScanListedItem;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewScanStoreService implements ReviewScanStore {
    private final ReviewRepository reviews;
    private final ReviewPracticeRepository practices;
    private final ReviewItemRepository items;
    private final ScanReuseCandidateQuery reuseCandidates;
    private final ReviewAssignmentQuery assignments;
    private final ResultRepository results;
    private final ItemWriter writer;
    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock clock;
    private final ReviewSnapshotQuery snapshots;
    private final ReviewRuntimeQuery runtime;

    @Override
    @Transactional(readOnly = true)
    public List<ScanExecutionSnapshot> active(int limit) {
        limit(limit);
        return jdbc
                .sql("SELECT workspace_id,id FROM review WHERE kind='SCAN' AND status IN ('QUEUED','RUNNING')"
                        + " ORDER BY created_at,id::text LIMIT :limit")
                .param("limit", limit)
                .query((rs, n) -> new ScanId(rs.getLong(1), Objects.requireNonNull(rs.getObject(2, UUID.class))))
                .list()
                .stream()
                .map(id -> reviews.findByIdAndWorkspaceId(id.reviewId(), id.workspaceId())
                        .orElseThrow())
                .map(this::snapshot)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ScanExecutionSnapshot lock(long workspaceId, UUID reviewId) {
        var review = reviews.lockByIdAndWorkspaceId(reviewId, workspaceId).orElseThrow();
        if (review.getKind() != ReviewKind.SCAN) throw new IllegalArgumentException("Select a scan review.");
        return snapshot(review);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewAssignmentSnapshot> pending(long workspaceId, UUID reviewId, int limit) {
        limit(limit);
        var review = reviews.findByIdAndWorkspaceId(reviewId, workspaceId).orElseThrow();
        if (review.getKind() != ReviewKind.SCAN) throw new IllegalArgumentException("Select a scan review.");
        if (review.getStatus().isTerminal()
                || review.getChecks().path("cancellationRequested").asBoolean()) return List.of();
        var activeJobs = runtime.activeJobIds(workspaceId, reviewId);
        return jdbc
                .sql("SELECT m.practice_id,m.item_id FROM review_item m JOIN item i ON i.id=m.item_id"
                        + " AND i.workspace_id=m.workspace_id WHERE m.workspace_id=:ws AND m.review_id=:review"
                        + " AND m.state='LISTED' AND (m.job_id IS NULL OR m.job_id<>ALL(:active))"
                        + " ORDER BY i.path,m.item_id,m.practice_id LIMIT :limit")
                .param("ws", workspaceId)
                .param("review", reviewId)
                .param("limit", limit)
                .param("active", new SqlArrayValue("uuid", activeJobs.toArray()))
                .query((rs, n) -> new ReviewAssignmentId(reviewId, rs.getLong(1), rs.getLong(2)))
                .list()
                .stream()
                .map(id -> assignments
                        .find(workspaceId, id)
                        .orElseThrow(() -> new IllegalStateException("The pending assignment has no frozen proof.")))
                .toList();
    }

    private ScanExecutionSnapshot snapshot(Review review) {
        var counts = jdbc.sql("SELECT COUNT(*) AS listed,COUNT(*) FILTER(WHERE state='JUDGED') AS judged,"
                        + " COUNT(*) FILTER(WHERE state='REUSED') AS reused,"
                        + " COUNT(*) FILTER(WHERE state='WITHHELD') AS withheld FROM review_item"
                        + " WHERE workspace_id=:ws AND review_id=:review")
                .param("ws", review.getWorkspaceId())
                .param("review", review.getId())
                .query((rs, n) -> new Counts(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)))
                .single();
        var listedPractices =
                practices
                        .findByWorkspaceIdAndReviewIdOrderByPracticeIdAsc(review.getWorkspaceId(), review.getId())
                        .stream()
                        .filter(p -> p.getItemsListed() != null)
                        .map(p -> p.getPracticeId())
                        .collect(Collectors.toUnmodifiableSet());
        var inventory = review.getInventoryReceipt();
        return new ScanExecutionSnapshot(
                snapshots.find(review.getWorkspaceId(), review.getId()).orElseThrow(),
                review.getStatus(),
                Objects.requireNonNull(review.getBudgetUsd(), "A scan must have a budget."),
                review.getCreatedAt(),
                review.getChecks().path("cancellationRequested").asBoolean(),
                inventory != null,
                inventory != null && inventory.path("complete").asBoolean(),
                listedPractices,
                counts.listed(),
                counts.judged(),
                counts.reused(),
                counts.withheld());
    }

    private static void limit(int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Use a scan limit from 1 to 1000.");
    }

    private record ScanId(long workspaceId, UUID reviewId) {}

    private record Counts(long listed, long judged, long reused, long withheld) {}

    @Override
    @Transactional
    public void inventory(long workspaceId, UUID reviewId, List<Long> presentItemIds, boolean complete) {
        var review = active(workspaceId, reviewId);
        var present = Set.copyOf(presentItemIds);
        var previous = jdbc.sql(
                        "SELECT id FROM item WHERE workspace_id=:ws AND parent_id=:parent AND kind='scm.file' AND"
                                + " removed_at IS NULL AND created_at<:before ORDER BY id FOR UPDATE")
                .param("ws", workspaceId)
                .param("parent", review.getItemId())
                .param("before", Timestamp.from(review.getCreatedAt()))
                .query((rs, n) -> rs.getLong(1))
                .list();
        if (!present.isEmpty()) {
            var count = jdbc.sql("SELECT COUNT(*) FROM item WHERE workspace_id=:ws AND parent_id=:parent AND"
                            + " kind='scm.file' AND id=ANY(:ids)")
                    .param("ws", workspaceId)
                    .param("parent", review.getItemId())
                    .param("ids", new SqlArrayValue("bigint", present.toArray()))
                    .query((rs, n) -> rs.getLong(1))
                    .single();
            if (count != present.size())
                throw new IllegalArgumentException("The inventory contains another repository's item.");
        }
        var missing = previous.stream().filter(id -> !present.contains(id)).toList();
        var guard = !complete
                ? RemovalGuardState.INCOMPLETE_LISTING
                : missing.isEmpty()
                        ? RemovalGuardState.NOT_NEEDED
                        : missing.size() * 5L >= previous.size()
                                ? RemovalGuardState.SUSPECT_LISTING
                                : RemovalGuardState.APPLIED;
        var receipt = json.createObjectNode()
                .put("complete", complete)
                .put("files", present.size())
                .put("previousFiles", previous.size())
                .put("wouldRemove", missing.size())
                .put("removalGuard", guard.name());
        review.setInventoryReceipt(receipt);
        if (complete) for (var id : present.stream().sorted().toList()) writer.restore(workspaceId, id);
        if (guard == RemovalGuardState.APPLIED)
            for (var id : missing) {
                writer.remove(workspaceId, id, clock.instant());
                results.endCurrent(workspaceId, id, ResultEndReason.REMOVED, clock.instant());
            }
    }

    @Override
    @Transactional
    public void list(long workspaceId, UUID reviewId, long practiceId, List<ScanListedItem> members, boolean complete) {
        var review = active(workspaceId, reviewId);
        if (review.getInventoryReceipt() == null)
            throw new IllegalStateException("Capture the full inventory before practice selection.");
        var practice = practices
                .findByWorkspaceIdAndReviewIdAndPracticeId(workspaceId, reviewId, practiceId)
                .orElseThrow();
        if (Boolean.TRUE.equals(practice.getListingComplete()))
            throw new IllegalStateException("A completed listing is immutable.");
        if (members.stream().map(m -> m.item().itemId()).distinct().count() != members.size())
            throw new IllegalArgumentException("A listing contains duplicate files.");
        for (var member : members.stream()
                .sorted(Comparator.comparingLong(m -> m.item().itemId()))
                .toList()) {
            if (member.item().workspaceId() != workspaceId
                    || !Long.valueOf(review.getItemId()).equals(member.item().parentId())
                    || !member.item().kind().value().equals("scm.file"))
                throw new IllegalArgumentException("The listed file belongs to another repository.");
            var existing = items.findByWorkspaceIdAndReviewIdAndPracticeIdAndItemId(
                    workspaceId, reviewId, practiceId, member.item().itemId());
            if (existing.isPresent()) {
                if (!member.version().value().equals(existing.orElseThrow().getItemVersion()))
                    throw new IllegalArgumentException("A listed version cannot change.");
                continue;
            }
            items.save(ReviewItem.builder()
                    .workspaceId(workspaceId)
                    .reviewId(reviewId)
                    .practiceId(practiceId)
                    .itemId(member.item().itemId())
                    .itemVersion(member.version().value())
                    .detail(json.createObjectNode().putNull("developerId"))
                    .build());
        }
        practice.setItemsListed(Math.toIntExact(
                jdbc.sql("SELECT COUNT(*) FROM review_item WHERE workspace_id=:ws AND review_id=:review AND"
                                + " practice_id=:practice")
                        .param("ws", workspaceId)
                        .param("review", reviewId)
                        .param("practice", practiceId)
                        .query((rs, n) -> rs.getLong(1))
                        .single()));
        practice.setListingComplete(complete);
        if (complete && Integer.valueOf(0).equals(practice.getItemsListed()))
            practice.setStatus(ReviewPracticeStatus.DONE);
    }

    @Override
    @Transactional
    public Optional<UUID> reuse(long workspaceId, ReviewAssignmentId id) {
        var review = active(workspaceId, id.reviewId());
        if (!review.getChecks().path("reuse").asBoolean(true)) return Optional.empty();
        var member = items.lockAssignment(workspaceId, id.reviewId(), id.practiceId(), id.itemId())
                .orElseThrow();
        if (member.getState() == ReviewItemState.REUSED) return Optional.ofNullable(member.getResultId());
        if (member.getState() != ReviewItemState.LISTED || member.getJobId() != null) return Optional.empty();
        var candidate = reuseCandidates.find(workspaceId, id);
        if (candidate.isEmpty()) return Optional.empty();
        var result = candidate.orElseThrow().result();
        var captured = candidate.orElseThrow().custody();
        var detail = member.getDetail() == null
                ? json.createObjectNode()
                : (ObjectNode) member.getDetail().deepCopy();
        detail.set("custody", json.valueToTree(captured));
        member.setDetail(detail);
        member.setState(ReviewItemState.REUSED);
        member.setResultId(result.getId());
        return Optional.of(result.getId());
    }

    private Review active(long workspaceId, UUID reviewId) {
        var review = reviews.lockByIdAndWorkspaceId(reviewId, workspaceId).orElseThrow();
        if (review.getKind() != ReviewKind.SCAN
                || review.getStatus().isTerminal()
                || review.getChecks().path("cancellationRequested").asBoolean())
            throw new IllegalStateException("This scan no longer accepts work.");
        return review;
    }
}
