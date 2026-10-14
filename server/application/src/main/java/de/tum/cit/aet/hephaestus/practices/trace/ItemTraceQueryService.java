package de.tum.cit.aet.hephaestus.practices.trace;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.FoundBy;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventReason;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.ReviewOn;
import de.tum.cit.aet.hephaestus.practices.result.ResultPresentation;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultViewQuery;
import de.tum.cit.aet.hephaestus.practices.review.ProductReferenceQuery;
import de.tum.cit.aet.hephaestus.practices.review.ReviewPracticeRepository;
import de.tum.cit.aet.hephaestus.practices.review.ReviewSummaryQueryService;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemEventDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.feedbackmode.FeedbackModeResolver;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.spi.CurrentDeveloperLookup;
import de.tum.cit.aet.hephaestus.practices.spi.EvidenceAuthorization;
import de.tum.cit.aet.hephaestus.practices.trace.dto.ItemTraceDTO;
import de.tum.cit.aet.hephaestus.practices.trace.dto.OwnItemEventDTO;
import de.tum.cit.aet.hephaestus.practices.trace.dto.OwnItemTraceDTO;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemTraceQueryService {
    private final ProductReferenceQuery references;
    private final ReviewSummaryQueryService reviewQuery;
    private final ReviewPracticeRepository assignments;
    private final ResultRepository results;
    private final ResultViewQuery views;
    private final ResultPresentation presentation;
    private final PracticeRepository practices;
    private final PracticeVersionRepository versions;
    private final CurrentDeveloperLookup developer;
    private final EvidenceAuthorization authorization;
    private final WorkspaceReviewDefaultsProvider defaults;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    public ItemTraceDTO trace(long ws, long itemId) {
        var item = requireItem(ws, itemId);
        var history = reviewsForItem(ws, itemId);
        var resultRows = results.findByWorkspaceIdAndItemIdOrderByRecordedAtDescIdDesc(ws, itemId);
        var practiceRows = practices.findByWorkspaceIdAndKind(ws, new ItemKind(item.kind()));
        var trace = practiceRows.stream()
                .map(p -> {
                    Review review = history.stream()
                            .filter(r -> assignments
                                    .findByWorkspaceIdAndReviewIdAndPracticeId(ws, r.getId(), p.getId())
                                    .isPresent())
                            .findFirst()
                            .orElse(null);
                    var assigned = review == null
                            ? null
                            : assignments
                                    .findByWorkspaceIdAndReviewIdAndPracticeId(ws, review.getId(), p.getId())
                                    .orElse(null);
                    var version = assigned == null || assigned.getPracticeVersionId() == null
                            ? null
                            : versions.findById(assigned.getPracticeVersionId())
                                    .filter(v -> v.getWorkspaceId() == ws)
                                    .orElse(null);
                    if (review != null && assigned == null) return null;
                    var raw = version == null && review == null
                            ? p.getCurrentVersion().getDefinition()
                            : version == null ? null : version.getDefinition();
                    boolean known = raw != null
                            && raw.has("about")
                            && raw.path("reviewOn").isObject();
                    var reviewOn = raw != null && raw.path("reviewOn").isObject()
                            ? json.convertValue(raw.path("reviewOn"), ReviewOn.class)
                            : new ReviewOn(List.of(), Map.of());
                    var about = raw != null && raw.path("about").isString()
                            ? json.convertValue(raw.path("about"), AboutRole.class)
                            : null;
                    return PracticeTraceDeriver.derive(new TraceInputs(
                            p.getSlug(),
                            reviewOn,
                            about,
                            known,
                            FeedbackModeResolver.effectiveModeOf(
                                    p, defaults.forWorkspace(ws).defaultFeedbackMode()),
                            review == null ? null : reviewQuery.ref(review),
                            assigned,
                            resultRows.stream()
                                    .filter(r -> r.getPracticeId() == p.getId()
                                            && (review == null
                                                    || r.getReviewId().equals(review.getId())))
                                    .toList()));
                })
                .filter(Objects::nonNull)
                .toList();
        return new ItemTraceDTO(
                item,
                events(ws, itemId, PageRequest.ofSize(100)).getContent(),
                history.stream().map(reviewQuery::ref).toList(),
                trace);
    }

    public OwnItemTraceDTO own(long ws, long itemId, UUID reviewId) {
        var item = requireItem(ws, itemId);
        var review = reviewQuery.require(ws, reviewId);
        long account = developer.currentDeveloperIdElseThrow();
        var found = results.findByWorkspaceIdAndReviewIdOrderByRecordedAtAscIdAsc(ws, reviewId).stream()
                .filter(r -> Objects.equals(r.getDeveloperId(), account) && r.getItemId() == itemId)
                .toList();
        var permitted = authorization.permitsAll(ws, found, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        var visible = found.stream().filter(r -> permitted.contains(r.getId())).toList();
        if (visible.isEmpty()) throw new EntityNotFoundException("Review", reviewId.toString());
        return new OwnItemTraceDTO(
                item,
                reviewQuery.ref(review),
                events(ws, itemId, PageRequest.ofSize(100)).stream()
                        .map(e -> new OwnItemEventDTO(e.name(), e.occurredAt()))
                        .toList(),
                views.views(ws, visible).stream()
                        .map(v -> presentation.list(v, true))
                        .toList());
    }

    public ItemRefDTO requireItem(long ws, long itemId) {
        var item = references.items(ws, List.of(itemId)).get(itemId);
        if (item == null) throw new EntityNotFoundException("Item", Long.toString(itemId));
        return item;
    }

    public List<Review> reviewsForItem(long ws, long itemId) {
        var ids = jdbc.sql("""
            SELECT q.id FROM review q WHERE q.workspace_id=:ws AND (q.item_id=:item OR EXISTS(SELECT 1 FROM review_item m WHERE m.workspace_id=q.workspace_id AND m.review_id=q.id AND m.item_id=:item))
            ORDER BY q.ordered_at DESC,q.id::text DESC LIMIT 100
            """)
                .param("ws", ws)
                .param("item", itemId)
                .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                .list();
        return ids.stream().map(id -> reviewQuery.require(ws, id)).toList();
    }

    public Page<ItemEventDTO> events(long ws, long itemId, Pageable page) {
        requireItem(ws, itemId);
        long total = jdbc.sql("SELECT count(*) FROM item_event WHERE workspace_id=:ws AND item_id=:item")
                .param("ws", ws)
                .param("item", itemId)
                .query((rs, n) -> rs.getLong(1))
                .single();
        var content = jdbc.sql("SELECT * FROM item_event WHERE workspace_id=:ws AND item_id=:item ORDER BY occurred_at"
                        + " DESC,id::text DESC LIMIT :limit OFFSET :offset")
                .param("ws", ws)
                .param("item", itemId)
                .param("limit", page.getPageSize())
                .param("offset", page.getOffset())
                .query((rs, n) -> new ItemEventDTO(
                        rs.getObject("id", UUID.class),
                        Long.toString(itemId),
                        rs.getString("name"),
                        rs.getString("item_version"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        FoundBy.valueOf(rs.getString("found_by")),
                        ItemEventState.valueOf(rs.getString("state")),
                        rs.getString("reason") == null ? null : ItemEventReason.valueOf(rs.getString("reason")),
                        instant(rs.getObject("decided_at", OffsetDateTime.class)),
                        rs.getObject("review_id", UUID.class)))
                .list();
        return new PageImpl<>(content, page, total);
    }

    private static @Nullable Instant instant(@Nullable OffsetDateTime date) {
        return date == null ? null : date.toInstant();
    }
}
