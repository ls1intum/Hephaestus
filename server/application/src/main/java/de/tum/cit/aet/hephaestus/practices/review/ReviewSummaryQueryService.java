package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.FoundBy;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemEventState;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.ReviewFeedbackCountsDTO;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeVersion;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemEventDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewDetailDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewItemCountsDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewJobDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewPracticeDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewRefDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewResultCountsDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewRuntimeDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewStageTimingDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewSummaryDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewUsageAttemptDTO;
import de.tum.cit.aet.hephaestus.practices.review.dto.ReviewUsageDTO;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.review.model.ReviewPractice;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.PracticeScriptRunDTO;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRequestReason;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewRuntimeSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewScriptQuery;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewSummaryQueryService {
    private final ReviewRepository reviews;
    private final ReviewPracticeRepository assignments;
    private final PracticeRepository practices;
    private final PracticeVersionRepository versions;
    private final ProductReferenceQuery references;
    private final ReviewRuntimeQuery runtime;
    private final ReviewScriptQuery scripts;
    private final JdbcClient jdbc;
    private final JsonMapper json;

    public Review require(long ws, UUID id) {
        return reviews.findByIdAndWorkspaceId(id, ws)
                .orElseThrow(() -> new EntityNotFoundException("Review", id.toString()));
    }

    public Page<ReviewSummaryDTO> list(long ws, ReviewFilterParams filter, Pageable page) {
        var where = new StringBuilder(" WHERE q.workspace_id=:ws");
        var params = new LinkedHashMap<String, Object>();
        params.put("ws", ws);
        add(where, params, "q.id=:id", "id", filter.reviewId());
        add(where, params, "q.item_id=:item", "item", filter.itemId());
        add(
                where,
                params,
                "q.kind=:kind",
                "kind",
                filter.kind() == null ? null : filter.kind().name());
        add(where, params, "q.created_at>=:from", "from", filter.from());
        add(where, params, "q.created_at<:to", "to", filter.to());
        if (filter.status() != null && !filter.status().isEmpty())
            add(
                    where,
                    params,
                    "q.status IN (:statuses)",
                    "statuses",
                    filter.status().stream().map(Enum::name).toList());
        if (filter.developerId() != null || filter.practiceSlug() != null || filter.groupSlug() != null) {
            var basis = new StringBuilder("EXISTS(SELECT 1 FROM result r JOIN practice p ON p.id=r.practice_id AND"
                    + " p.workspace_id=r.workspace_id LEFT JOIN practice_group g ON g.id=p.group_id AND"
                    + " g.workspace_id=p.workspace_id WHERE r.workspace_id=q.workspace_id AND r.review_id=q.id AND"
                    + " NOT EXISTS(SELECT 1 FROM result_mark m WHERE m.workspace_id=r.workspace_id AND"
                    + " m.result_id=r.id AND m.kind='INCORRECT' AND m.removed_at IS NULL)");
            add(basis, params, "r.developer_id=:developer", "developer", filter.developerId());
            add(basis, params, "p.slug=:practice", "practice", filter.practiceSlug());
            add(basis, params, "g.slug=:group", "group", filter.groupSlug());
            where.append(" AND " + basis + ")");
        }
        long total = jdbc.sql("SELECT count(*) FROM review q" + where)
                .params(params)
                .query((rs, n) -> rs.getLong(1))
                .single();
        params.put("limit", page.getPageSize());
        params.put("offset", page.getOffset());
        var ids = jdbc.sql("SELECT q.id FROM review q" + where
                        + " ORDER BY q.created_at DESC,q.id::text DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .query((rs, n) -> Objects.requireNonNull(rs.getObject(1, UUID.class)))
                .list();
        return new PageImpl<>(ids.stream().map(id -> summary(require(ws, id))).toList(), page, total);
    }

    public ReviewRefDTO ref(Review review) {
        return new ReviewRefDTO(
                review.getId(),
                review.getKind(),
                item(review),
                review.getItemVersion(),
                review.getOrigin(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getCompletedAt());
    }

    public ReviewDTO view(long ws, UUID id) {
        return json.convertValue(summary(require(ws, id)), ReviewDTO.class);
    }

    public ReviewSummaryDTO summary(Review review) {
        var jobs = runtime.runtime(review.getWorkspaceId(), review.getId())
                .map(ReviewRuntimeSnapshot::jobs)
                .orElse(List.of());
        var pinned = practices(review);
        var model =
                assignments
                        .findByWorkspaceIdAndReviewIdOrderByPracticeIdAsc(review.getWorkspaceId(), review.getId())
                        .stream()
                        .map(ReviewPractice::getMethod)
                        .filter(Objects::nonNull)
                        .flatMap(m -> m.models().stream())
                        .filter(m -> m.slot().equals("review") && m.model() != null)
                        .map(m -> Objects.requireNonNull(m.model()).modelId())
                        .findFirst()
                        .orElse(null);
        return new ReviewSummaryDTO(
                review.getId(),
                review.getKind(),
                item(review),
                review.getItemVersion(),
                review.getOrigin(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getCompletedAt(),
                event(review),
                review.getRequestedById() == null
                        ? null
                        : references
                                .accounts(review.getWorkspaceId(), List.of(review.getRequestedById()))
                                .get(review.getRequestedById()),
                review.getBatchId(),
                review.getOrderedAt(),
                review.getStopReason(),
                pinned,
                resultCounts(review, null),
                feedbackCounts(review.getWorkspaceId(), review.getId()),
                jobs.size(),
                (int) jobs.stream()
                        .filter(j -> j.status() == ReviewJobStatus.QUEUED || j.status() == ReviewJobStatus.RUNNING)
                        .count(),
                model);
    }

    public ReviewDetailDTO detail(long ws, UUID id) {
        var r = require(ws, id);
        var s = summary(r);
        var snapshot = runtime.runtime(ws, id).orElse(null);
        var runtimeDTO = snapshot == null
                ? null
                : new ReviewRuntimeDTO(snapshot.jobs().stream()
                        .map(j -> new ReviewJobDTO(
                                j.id(),
                                j.status(),
                                j.attempt(),
                                j.processingStatus(),
                                j.availableAt(),
                                j.startedAt(),
                                j.completedAt(),
                                j.exitCode(),
                                j.errorMessage(),
                                j.traceId()))
                        .toList());
        var times = snapshot == null
                ? List.<ReviewStageTimingDTO>of()
                : snapshot.jobs().stream()
                        .map(j -> new ReviewStageTimingDTO(
                                "RUN",
                                j.startedAt(),
                                j.completedAt(),
                                j.startedAt() == null || j.completedAt() == null
                                        ? null
                                        : Math.toIntExact(Duration.between(j.startedAt(), j.completedAt())
                                                .toMillis())))
                        .toList();
        return new ReviewDetailDTO(
                s.id(),
                s.kind(),
                s.item(),
                s.itemVersion(),
                s.origin(),
                s.status(),
                s.createdAt(),
                s.completedAt(),
                s.startedBy(),
                s.requestedBy(),
                s.batchId(),
                s.orderedAt(),
                s.stopReason(),
                s.practices(),
                s.results(),
                s.feedback(),
                s.jobCount(),
                s.activeJobCount(),
                s.model(),
                runtimeDTO,
                usage(ws, id),
                times,
                null);
    }

    public ReviewUsageDTO usage(long ws, UUID id) {
        require(ws, id);
        var u = runtime.usage(ws, id);
        return new ReviewUsageDTO(
                u.costUsd() == null ? null : u.costUsd().toPlainString(),
                u.priced(),
                u.calls(),
                u.inputTokens(),
                u.outputTokens(),
                u.attempts().stream()
                        .map(a -> new ReviewUsageAttemptDTO(
                                a.jobId(),
                                a.attempt(),
                                a.calls(),
                                a.inputTokens(),
                                a.outputTokens(),
                                a.reasoningTokens(),
                                a.cacheReadTokens(),
                                a.cacheWriteTokens(),
                                a.costUsd() == null ? null : a.costUsd().toPlainString(),
                                a.pricingState()))
                        .toList());
    }

    public List<PracticeScriptRunDTO> scripts(long ws, UUID id) {
        require(ws, id);
        return scripts.forReview(ws, id);
    }

    public List<ReviewPracticeDTO> practices(Review review) {
        long ws = review.getWorkspaceId();
        var practicesById = practices.findByWorkspaceId(ws).stream().collect(Collectors.toMap(Practice::getId, p -> p));
        var rows = assignments.findByWorkspaceIdAndReviewIdOrderByPracticeIdAsc(ws, review.getId());
        var versionById = versions
                .findAllById(rows.stream()
                        .map(ReviewPractice::getPracticeVersionId)
                        .filter(Objects::nonNull)
                        .toList())
                .stream()
                .filter(v -> v.getWorkspaceId() == ws)
                .collect(Collectors.toMap(PracticeVersion::getId, v -> v));
        var scriptRows = scripts.forReview(ws, review.getId());
        return rows.stream()
                .map(a -> {
                    var p = Objects.requireNonNull(practicesById.get(a.getPracticeId()));
                    var v = versionById.get(a.getPracticeVersionId());
                    var counts = jdbc.sql(
                                    "SELECT COUNT(*) AS listed,COUNT(*) FILTER(WHERE state IN ('JUDGED','REUSED')) AS"
                                            + " reviewed,COUNT(*) FILTER(WHERE state='REUSED') AS reused,COUNT(*)"
                                            + " FILTER(WHERE state='WITHHELD') AS withheld,COUNT(*) FILTER(WHERE"
                                            + " state='LISTED') AS pending FROM review_item WHERE workspace_id=:ws AND"
                                            + " review_id=:review AND practice_id=:practice")
                            .param("ws", ws)
                            .param("review", review.getId())
                            .param("practice", a.getPracticeId())
                            .query((rs, n) -> new ReviewItemCountsDTO(
                                    Math.toIntExact(rs.getLong("listed")),
                                    Math.toIntExact(rs.getLong("reviewed")),
                                    Math.toIntExact(rs.getLong("reused")),
                                    Math.toIntExact(rs.getLong("withheld")),
                                    review.getStatus().isTerminal() ? Math.toIntExact(rs.getLong("pending")) : 0))
                            .single();
                    return new ReviewPracticeDTO(
                            p.getSlug(),
                            v == null || v.getName() == null ? "Unknown practice version" : v.getName(),
                            a.getPracticeVersionId() == null
                                    ? null
                                    : a.getPracticeVersionId().toString(),
                            v == null ? null : v.getNumber(),
                            a.getStatus(),
                            a.getReason(),
                            counts,
                            resultCounts(review, a.getPracticeId()),
                            scriptRows.stream()
                                    .filter(run ->
                                            run.practiceId().equals(p.getId().toString()))
                                    .max(Comparator.comparingInt(PracticeScriptRunDTO::attempt))
                                    .orElse(null));
                })
                .toList();
    }

    public ItemRefDTO item(Review r) {
        return Objects.requireNonNull(
                references.items(r.getWorkspaceId(), List.of(r.getItemId())).get(r.getItemId()));
    }

    public @Nullable ItemEventDTO event(Review r) {
        if (r.getEventId() == null) return null;
        return jdbc.sql("SELECT * FROM item_event WHERE workspace_id=:ws AND id=:id")
                .param("ws", r.getWorkspaceId())
                .param("id", r.getEventId())
                .query((rs, n) -> new ItemEventDTO(
                        rs.getObject("id", UUID.class),
                        Long.toString(rs.getLong("item_id")),
                        rs.getString("name"),
                        rs.getString("item_version"),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        FoundBy.valueOf(rs.getString("found_by")),
                        ItemEventState.valueOf(rs.getString("state")),
                        rs.getString("reason") == null ? null : ReviewRequestReason.valueOf(rs.getString("reason")),
                        instant(rs.getObject("decided_at", OffsetDateTime.class)),
                        rs.getObject("review_id", UUID.class)))
                .optional()
                .orElse(null);
    }

    private ReviewResultCountsDTO resultCounts(Review review, @Nullable Long practiceId) {
        var params = new HashMap<String, Object>();
        params.put("ws", review.getWorkspaceId());
        params.put("id", review.getId());
        String scope = review.getKind() == ReviewKind.SCAN
                ? "EXISTS(SELECT 1 FROM review_item member WHERE member.workspace_id=r.workspace_id AND"
                        + " member.review_id=:id AND member.result_id=r.id)"
                : "r.review_id=:id";
        if (practiceId != null) {
            scope += " AND r.practice_id=:practice";
            params.put("practice", practiceId);
        }
        return jdbc.sql("SELECT COUNT(*) FILTER(WHERE outcome='MET') AS met,COUNT(*) FILTER(WHERE outcome='NOT_MET')"
                        + " AS not_met,COUNT(*) FILTER(WHERE outcome='DOES_NOT_APPLY') AS does_not_apply,COUNT(*)"
                        + " FILTER(WHERE outcome='COULD_NOT_DETERMINE') AS could_not_determine FROM result r WHERE"
                        + " r.workspace_id=:ws AND "
                        + scope)
                .params(params)
                .query((rs, n) -> new ReviewResultCountsDTO(
                        Math.toIntExact(rs.getLong("met")),
                        Math.toIntExact(rs.getLong("not_met")),
                        Math.toIntExact(rs.getLong("does_not_apply")),
                        Math.toIntExact(rs.getLong("could_not_determine"))))
                .single();
    }

    public static ReviewResultCountsDTO counts(Collection<Result> rows) {
        var c = new EnumMap<ResultOutcome, Integer>(ResultOutcome.class);
        rows.forEach(r -> c.merge(r.getOutcome(), 1, Integer::sum));
        return new ReviewResultCountsDTO(
                c.getOrDefault(ResultOutcome.MET, 0),
                c.getOrDefault(ResultOutcome.NOT_MET, 0),
                c.getOrDefault(ResultOutcome.DOES_NOT_APPLY, 0),
                c.getOrDefault(ResultOutcome.COULD_NOT_DETERMINE, 0));
    }

    public ReviewFeedbackCountsDTO feedbackCounts(long ws, UUID id) {
        var c = new EnumMap<FeedbackState, Integer>(FeedbackState.class);
        jdbc.sql("SELECT state,count(*) AS n FROM feedback WHERE workspace_id=:ws AND review_id=:id GROUP BY state")
                .param("ws", ws)
                .param("id", id)
                .query((rs, n) -> Map.entry(FeedbackState.valueOf(rs.getString("state")), rs.getInt("n")))
                .list()
                .forEach(e -> c.put(e.getKey(), e.getValue()));
        return new ReviewFeedbackCountsDTO(
                c.getOrDefault(FeedbackState.AWAITING_APPROVAL, 0),
                c.getOrDefault(FeedbackState.READY, 0),
                c.getOrDefault(FeedbackState.DELIVERED, 0),
                c.getOrDefault(FeedbackState.UNCONFIRMED, 0),
                c.getOrDefault(FeedbackState.WITHHELD, 0),
                c.getOrDefault(FeedbackState.REJECTED, 0),
                c.getOrDefault(FeedbackState.REPLACED, 0),
                c.getOrDefault(FeedbackState.FAILED, 0));
    }

    private static @Nullable Instant instant(@Nullable OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static void add(
            StringBuilder where, Map<String, Object> params, String sql, String key, @Nullable Object value) {
        if (value != null) {
            where.append(" AND " + sql);
            params.put(key, value);
        }
    }
}
