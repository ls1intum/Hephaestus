package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackContentDigest;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecision;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackDecisionRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.dto.FeedbackDecisionDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackBasisDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackCheckDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackCopyCountsDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackCopyDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackResponseDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.FeedbackSendDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.ReviewFeedbackDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.dto.ReviewFeedbackDetailDTO;
import de.tum.cit.aet.hephaestus.practices.feedback.response.FeedbackResponse;
import de.tum.cit.aet.hephaestus.practices.feedback.response.FeedbackResponseRepository;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultViewQuery;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.review.ProductReferenceQuery;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.review.dto.ItemRefDTO;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewResultQueryService;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReasonCountDTO;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDecisionAction;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackLocation;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewFeedbackSort;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnServerRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackReadService {
    private static final int PREVIEW_LENGTH = 240;
    private final FeedbackRepository feedback;
    private final FeedbackBasisRepository basis;
    private final FeedbackCopyRepository copies;
    private final FeedbackSendRepository sends;
    private final FeedbackDecisionRepository decisions;
    private final FeedbackResponseRepository responses;
    private final FeedbackVisibilityService visibility;
    private final FeedbackContentDigest digests;
    private final ProductReferenceQuery references;
    private final ResultRepository results;
    private final ResultViewQuery views;
    private final ResultVisibilityPolicy evidence;
    private final ReviewResultQueryService resultPresentation;
    private final ReviewRepository reviews;
    private final FeedbackQueryRepository queries;

    public Page<ReviewFeedbackDTO> list(long workspaceId, FeedbackQueryFilter filter, Pageable pageable) {
        var spec = specification(workspaceId, filter);
        if (filter.location() == null) return feedback.findAll(spec, pageable).map(this::summary);
        var all = feedback.findAll(spec, ordering(filter)).stream()
                .map(this::summary)
                .filter(row -> filter.location() == row.location())
                .toList();
        int from = (int) Math.min(pageable.getOffset(), all.size()),
                to = Math.min(from + pageable.getPageSize(), all.size());
        return new PageImpl<>(all.subList(from, to), pageable, all.size());
    }

    public List<ReasonCountDTO> locations(long workspaceId, FeedbackQueryFilter filter) {
        return counts(workspaceId, filter, true);
    }

    public List<ReasonCountDTO> reasons(long workspaceId, FeedbackQueryFilter filter) {
        return counts(workspaceId, filter, false);
    }

    private List<ReasonCountDTO> counts(long workspaceId, FeedbackQueryFilter filter, boolean byLocation) {
        var counts = new TreeMap<String, Integer>();
        feedback.findAll(specification(workspaceId, filter)).stream()
                .map(this::summary)
                .filter(row -> filter.location() == null || filter.location() == row.location())
                .forEach(row -> {
                    String key = byLocation
                            ? row.location().name()
                            : row.stopReason() == null ? null : row.stopReason().name();
                    if (key != null) counts.merge(key, 1, Integer::sum);
                });
        return counts.entrySet().stream()
                .map(entry -> new ReasonCountDTO(entry.getKey(), description(entry.getKey()), entry.getValue()))
                .toList();
    }

    private static String description(String value) {
        String words = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    public ReviewFeedbackDetailDTO detail(long workspaceId, UUID id) {
        var row = required(workspaceId, id);
        var summary = summary(row);
        var edges = basis.findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(workspaceId, id);
        var support = results.findByWorkspaceIdAndIdIn(
                workspaceId,
                edges.stream().map(FeedbackBasis::getResultId).distinct().toList());
        var permitted = evidence.permitsHistory(workspaceId, support, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
        var resultViews = views.views(workspaceId, support).stream()
                .collect(Collectors.toMap(view -> view.result().getId(), view -> view));
        var bound = edges.stream()
                .map(edge -> new FeedbackBasisDTO(
                        edge.getResultId(),
                        edge.getProblemId(),
                        edge.getOrdinal(),
                        resultPresentation.bound(
                                Objects.requireNonNull(resultViews.get(edge.getResultId())),
                                permitted.contains(edge.getResultId()))))
                .toList();
        boolean readable = !edges.isEmpty()
                && support.size()
                        == edges.stream()
                                .map(FeedbackBasis::getResultId)
                                .distinct()
                                .count()
                && permitted.containsAll(support.stream().map(Result::getId).toList());
        var actualCopies = copies.findByWorkspaceIdAndFeedbackIdOrderByPositionAsc(workspaceId, id).stream()
                .map(copy -> redactedCopy(copy, readable))
                .toList();
        var activeSend = sends.findByFeedbackIdAndWorkspaceId(id, workspaceId).map(FeedbackSendDTO::from).stream()
                .toList();
        return new ReviewFeedbackDetailDTO(
                summary.id(),
                summary.reviewId(),
                summary.item(),
                summary.developer(),
                summary.destination(),
                summary.state(),
                summary.stopReason(),
                summary.location(),
                summary.replacesId(),
                summary.createdAt(),
                summary.deliveredAt(),
                summary.bodyPreview(),
                summary.bodyTruncated(),
                summary.resultCount(),
                summary.copyCounts(),
                summary.withdrawn(),
                summary.disagreedAt(),
                readable ? row.getBody() : null,
                bound,
                actualCopies,
                activeSend,
                decisionHistory(workspaceId, id),
                responses
                        .findCurrent(workspaceId, id)
                        .map(FeedbackResponseDTO::from)
                        .orElse(null),
                checks(row),
                readable ? digests.current(workspaceId, id) : null);
    }

    public List<FeedbackDecisionDTO> decisionHistory(long workspaceId, UUID id) {
        required(workspaceId, id);
        var rows = decisions.findByWorkspaceIdAndFeedbackIdOrderByDecidedAtDescIdDesc(workspaceId, id);
        var actors = references.accounts(
                workspaceId,
                rows.stream()
                        .map(row -> row.getActorAccountId())
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList());
        return rows.stream()
                .map(row -> FeedbackDecisionDTO.from(row, actors.get(row.getActorAccountId())))
                .toList();
    }

    public List<FeedbackCheckDTO> reviewChecks(long workspaceId, UUID reviewId) {
        var review = reviews.findByIdAndWorkspaceId(reviewId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Review", reviewId.toString()));
        return feedback.findByWorkspaceIdAndReviewIdOrderByCreatedAtAscIdAsc(workspaceId, review.getId()).stream()
                .flatMap(row -> checks(row).stream())
                .toList();
    }

    private static List<FeedbackCheckDTO> checks(Feedback row) {
        return row.getChecks().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .flatMap(entry -> entry.getValue().checks().stream())
                .map(FeedbackCheckDTO::from)
                .toList();
    }

    private Feedback required(long workspaceId, UUID id) {
        return feedback.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("Feedback", id.toString()));
    }

    private ReviewFeedbackDTO summary(Feedback row) {
        long workspaceId = row.getWorkspaceId();
        UUID id = row.getId();
        var item = row.getItemId() == null
                ? null
                : references.items(workspaceId, List.of(row.getItemId())).get(row.getItemId());
        var developer = Objects.requireNonNull(references
                .developers(workspaceId, List.of(row.getDeveloperId()))
                .get(row.getDeveloperId()));
        var children = copies.findByWorkspaceIdAndFeedbackIdOrderByPositionAsc(workspaceId, id);
        var copyCounts = new EnumMap<FeedbackCopyState, Integer>(FeedbackCopyState.class);
        children.forEach(copy -> copyCounts.merge(copy.getState(), 1, Integer::sum));
        var edgeIds = basis.findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(workspaceId, id).stream()
                .map(FeedbackBasis::getResultId)
                .distinct()
                .toList();
        var support = results.findByWorkspaceIdAndIdIn(workspaceId, edgeIds);
        boolean allowed = !support.isEmpty()
                && support.size() == edgeIds.size()
                && evidence.permitsHistory(workspaceId, support, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY)
                                .size()
                        == support.size();
        String body = allowed ? row.getBody() : null;
        var answer = responses.findCurrent(workspaceId, id);
        Instant disagreedAt = answer.filter(response -> response.getResolution() == FeedbackResolution.DISAGREE)
                .map(response -> response.getCreatedAt())
                .orElse(null);
        return new ReviewFeedbackDTO(
                id,
                row.getReviewId(),
                item,
                developer,
                row.getDestination(),
                row.getState(),
                row.getStopReason(),
                location(row, item),
                row.getReplacesId(),
                row.getCreatedAt(),
                row.getDeliveredAt(),
                body == null ? null : body.substring(0, Math.min(body.length(), PREVIEW_LENGTH)),
                body != null && body.length() > PREVIEW_LENGTH,
                edgeIds.size(),
                new FeedbackCopyCountsDTO(
                        copyCounts.getOrDefault(FeedbackCopyState.PLANNED, 0),
                        copyCounts.getOrDefault(FeedbackCopyState.WRITE_STARTED, 0),
                        copyCounts.getOrDefault(FeedbackCopyState.POSTED, 0),
                        copyCounts.getOrDefault(FeedbackCopyState.FAILED, 0),
                        copyCounts.getOrDefault(FeedbackCopyState.WITHHELD, 0),
                        copyCounts.getOrDefault(FeedbackCopyState.RETRACTED, 0)),
                visibility.withdrawn(workspaceId, id),
                disagreedAt);
    }

    private FeedbackLocation location(Feedback row, @Nullable ItemRefDTO item) {
        return switch (row.getDestination()) {
            case PRACTICE_PAGE -> FeedbackLocation.PRACTICE_PAGES;
            case ON_WORK ->
                item == null || item.provider() == null
                        ? FeedbackLocation.OTHER_WORK
                        : switch (item.provider()) {
                            case GITHUB -> FeedbackLocation.GITHUB;
                            case GITLAB -> FeedbackLocation.GITLAB;
                            case SLACK, OUTLINE -> FeedbackLocation.OTHER_WORK;
                        };
            case CONVERSATION ->
                queries.conversationSurface(row.getWorkspaceId(), row.getId())
                        .map(surface -> "SLACK_DM".equals(surface) ? FeedbackLocation.SLACK : FeedbackLocation.HEPH)
                        .orElse(FeedbackLocation.NEXT_CONVERSATION);
        };
    }

    private static FeedbackCopyDTO redactedCopy(FeedbackCopy copy, boolean readable) {
        if (readable) return FeedbackCopyDTO.from(copy);
        return new FeedbackCopyDTO(
                copy.getId(),
                copy.getKind(),
                copy.getPosition(),
                null,
                null,
                null,
                null,
                null,
                copy.getState(),
                copy.getWriteStartedAt(),
                copy.getExternalUrl(),
                copy.getExternalRef(),
                copy.getChatMessageId(),
                copy.getPostedAt(),
                copy.getStopReason(),
                copy.getLastError());
    }

    private Specification<Feedback> specification(long workspaceId, FeedbackQueryFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("workspaceId"), workspaceId));
            if (filter.state() != null) predicates.add(root.get("state").in(filter.state()));
            if (filter.stopReason() != null)
                predicates.add(root.get("stopReason").in(filter.stopReason()));
            if (filter.destination() != null)
                predicates.add(root.get("destination").in(filter.destination()));
            if (filter.reviewId() != null) predicates.add(cb.equal(root.get("reviewId"), filter.reviewId()));
            if (filter.itemId() != null) predicates.add(cb.equal(root.get("itemId"), filter.itemId()));
            if (filter.developerId() != null) predicates.add(cb.equal(root.get("developerId"), filter.developerId()));
            if (filter.itemKind() != null)
                predicates.add(root.get("itemId").in(queries.itemIds(workspaceId, filter.itemKind())));
            if (filter.withdrawn() != null) {
                var exists = visibilityPredicate(root, Objects.requireNonNull(query), cb, workspaceId);
                predicates.add(filter.withdrawn() ? exists : cb.not(exists));
            }
            if (filter.disagreed() != null) {
                var exists = disagreementPredicate(root, Objects.requireNonNull(query), cb, workspaceId);
                predicates.add(filter.disagreed() ? exists : cb.not(exists));
            }
            if (filter.from() != null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from()));
            if (filter.to() != null) predicates.add(cb.lessThan(root.get("createdAt"), filter.to()));
            if (filter.practiceSlug() != null || filter.groupSlug() != null) {
                var sub = Objects.requireNonNull(query).subquery(Integer.class);
                var edge = sub.from(FeedbackBasis.class);
                var result = sub.from(Result.class);
                var practice = sub.from(Practice.class);
                List<Predicate> support = new ArrayList<>();
                support.add(cb.equal(edge.get("workspaceId"), workspaceId));
                support.add(cb.equal(edge.get("feedbackId"), root.get("id")));
                support.add(cb.equal(result.get("workspaceId"), workspaceId));
                support.add(cb.equal(result.get("id"), edge.get("resultId")));
                support.add(cb.equal(practice.get("workspace").get("id"), result.get("workspaceId")));
                support.add(cb.equal(practice.get("id"), result.get("practiceId")));
                if (filter.practiceSlug() != null) support.add(cb.equal(practice.get("slug"), filter.practiceSlug()));
                if (filter.groupSlug() != null)
                    support.add(cb.equal(practice.get("group").get("slug"), filter.groupSlug()));
                sub.select(cb.literal(1)).where(support.toArray(Predicate[]::new));
                predicates.add(cb.exists(sub));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Sort ordering(FeedbackQueryFilter filter) {
        return Sort.by(
                filter.sort() == ReviewFeedbackSort.OLDEST ? Sort.Direction.ASC : Sort.Direction.DESC,
                "createdAt",
                "id");
    }

    private static Predicate visibilityPredicate(
            Root<Feedback> feedback, CriteriaQuery<?> query, CriteriaBuilder cb, long workspaceId) {
        var sub = query.subquery(Integer.class);
        var row = sub.from(FeedbackDecision.class);
        var newer = sub.subquery(Integer.class);
        var next = newer.from(FeedbackDecision.class);
        newer.select(cb.literal(1))
                .where(
                        cb.equal(next.get("workspaceId"), workspaceId),
                        cb.equal(next.get("feedbackId"), row.get("feedbackId")),
                        next.get("action").in(FeedbackDecisionAction.WITHDRAW, FeedbackDecisionAction.RESTORE),
                        later(cb, next, row, "decidedAt"));
        sub.select(cb.literal(1))
                .where(
                        cb.equal(row.get("workspaceId"), workspaceId),
                        cb.equal(row.get("feedbackId"), feedback.get("id")),
                        cb.equal(row.get("action"), FeedbackDecisionAction.WITHDRAW),
                        cb.not(cb.exists(newer)));
        return cb.exists(sub);
    }

    private static Predicate disagreementPredicate(
            Root<Feedback> feedback, CriteriaQuery<?> query, CriteriaBuilder cb, long workspaceId) {
        var sub = query.subquery(Integer.class);
        var row = sub.from(FeedbackResponse.class);
        var newer = sub.subquery(Integer.class);
        var next = newer.from(FeedbackResponse.class);
        newer.select(cb.literal(1))
                .where(
                        cb.equal(next.get("workspaceId"), workspaceId),
                        cb.equal(next.get("feedbackId"), row.get("feedbackId")),
                        later(cb, next, row, "createdAt"));
        sub.select(cb.literal(1))
                .where(
                        cb.equal(row.get("workspaceId"), workspaceId),
                        cb.equal(row.get("feedbackId"), feedback.get("id")),
                        cb.equal(row.get("resolution"), FeedbackResolution.DISAGREE),
                        cb.not(cb.exists(newer)));
        return cb.exists(sub);
    }

    private static Predicate later(CriteriaBuilder cb, Root<?> next, Root<?> current, String time) {
        return cb.or(
                cb.greaterThan(next.<Instant>get(time), current.<Instant>get(time)),
                cb.and(
                        cb.equal(next.get(time), current.get(time)),
                        cb.greaterThan(
                                next.get("id").as(String.class),
                                current.get("id").as(String.class))));
    }
}
