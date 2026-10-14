package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.feedback.decision.FeedbackContentDigest;
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
import de.tum.cit.aet.hephaestus.practices.feedback.response.FeedbackResponseRepository;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultViewQuery;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import de.tum.cit.aet.hephaestus.practices.review.ProductReferenceQuery;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.ReviewResultQueryService;
import de.tum.cit.aet.hephaestus.practices.reviewoutput.dto.ReasonCountDTO;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackLocation;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackResolution;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

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
    private final ObjectMapper mapper;

    public Page<ReviewFeedbackDTO> list(long workspaceId, FeedbackQueryFilter filter, Pageable pageable) {
        var page = queries.page(
                workspaceId,
                mapper.writeValueAsString(filter),
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
        var locations = page.isEmpty()
                ? Map.<UUID, FeedbackLocation>of()
                : queries
                        .locations(
                                workspaceId,
                                page.getContent().stream().map(Feedback::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(
                                FeedbackQueryRepository.LocationRow::getId,
                                FeedbackQueryRepository.LocationRow::getLocation));
        return page.map(row -> summary(row, Objects.requireNonNull(locations.get(row.getId()))));
    }

    public List<ReasonCountDTO> locations(long workspaceId, FeedbackQueryFilter filter) {
        return counts(workspaceId, filter, true);
    }

    public List<ReasonCountDTO> reasons(long workspaceId, FeedbackQueryFilter filter) {
        return counts(workspaceId, filter, false);
    }

    private List<ReasonCountDTO> counts(long workspaceId, FeedbackQueryFilter filter, boolean byLocation) {
        return queries.counts(workspaceId, mapper.writeValueAsString(filter), byLocation).stream()
                .map(row -> new ReasonCountDTO(row.getReason(), description(row.getReason()), row.getCount()))
                .toList();
    }

    private static String description(String value) {
        String words = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    public ReviewFeedbackDetailDTO detail(long workspaceId, UUID id) {
        var row = required(workspaceId, id);
        var summary = summary(
                row, queries.locations(workspaceId, List.of(id)).getFirst().getLocation());
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

    private ReviewFeedbackDTO summary(Feedback row, FeedbackLocation location) {
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
                location,
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
                copy.getLastError(),
                copy.getReadback());
    }
}
