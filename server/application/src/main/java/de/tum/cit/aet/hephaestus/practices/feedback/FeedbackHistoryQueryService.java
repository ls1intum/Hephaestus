package de.tum.cit.aet.hephaestus.practices.feedback;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.hephaestus.evidence.CitationSide;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.response.FeedbackResponse;
import de.tum.cit.aet.hephaestus.practices.feedback.response.FeedbackResponseRepository;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultPresentation;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultSourceAuthorization;
import de.tum.cit.aet.hephaestus.practices.result.ResultViewQuery;
import de.tum.cit.aet.hephaestus.practices.review.ReviewRepository;
import de.tum.cit.aet.hephaestus.practices.spi.Currentness;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackHistoryQuery;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.HistoryCustody;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnCaptureRole
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeedbackHistoryQueryService implements FeedbackHistoryQuery {
    private final FeedbackRepository feedback;
    private final FeedbackBasisRepository basis;
    private final FeedbackCopyRepository copies;
    private final FeedbackResponseRepository responses;
    private final FeedbackVisibilityService visibility;
    private final ResultRepository results;
    private final ResultHistoryQuery history;
    private final ResultViewQuery views;
    private final ResultPresentation presentation;
    private final ReviewRepository reviews;
    private final ObjectMapper mapper;
    private final FeedbackQueryRepository queries;
    private final ResultSourceAuthorization sourceAuthorization;
    private final PracticeRepository practices;

    @Override
    public List<FeedbackHistoryEntry> forDeveloper(
            long workspaceId, long developerAccountId, Instant since, int limit) {
        if (!queries.member(workspaceId, developerAccountId)) return List.of();
        Specification<Feedback> selected = (row, query, cb) -> cb.and(
                cb.equal(row.get("workspaceId"), workspaceId),
                cb.equal(row.get("developerId"), developerAccountId),
                cb.greaterThanOrEqualTo(row.get("createdAt"), since));
        return read(selected, developerAccountId, limit, SourceUsePurpose.CONVERSATIONAL_MENTORING);
    }

    @Override
    public List<FeedbackHistoryEntry> forItem(long workspaceId, long itemId, long viewerAccountId, int limit) {
        if (!queries.member(workspaceId, viewerAccountId)) return List.of();
        Specification<Feedback> selected = (row, query, cb) -> cb.and(
                cb.equal(row.get("workspaceId"), workspaceId),
                cb.equal(row.get("itemId"), itemId),
                cb.or(
                        cb.equal(row.get("developerId"), viewerAccountId),
                        cb.and(
                                cb.equal(row.get("destination"), FeedbackDestination.ON_WORK),
                                cb.isNotNull(row.get("deliveredAt")))));
        return read(selected, viewerAccountId, limit, SourceUsePurpose.PRACTICE_FEEDBACK_DELIVERY);
    }

    private List<FeedbackHistoryEntry> read(
            Specification<Feedback> selected, long viewerAccountId, int limit, SourceUsePurpose purpose) {
        if (limit < 1) throw new IllegalArgumentException("A history read needs a positive limit.");
        return feedback
                .findAll(selected, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt", "id")))
                .getContent()
                .stream()
                .map(row -> entry(row, viewerAccountId, purpose))
                .toList();
    }

    private FeedbackHistoryEntry entry(Feedback row, long viewerAccountId, SourceUsePurpose purpose) {
        long workspaceId = row.getWorkspaceId();
        var edges = basis.findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(workspaceId, row.getId());
        var ids = edges.stream().map(FeedbackBasis::getResultId).distinct().toList();
        var support = results.findByWorkspaceIdAndIdIn(workspaceId, ids);
        var authorized = row.getDestination() == FeedbackDestination.ON_WORK
                ? support.stream()
                        .flatMap(result -> publicSupport(result, purpose).stream())
                        .toList()
                : ids.stream()
                        .flatMap(id -> history.find(workspaceId, id, viewerAccountId, purpose).stream())
                        .map(entry -> new AuthorizedSupport(
                                entry.resultId(),
                                entry.practiceSlug(),
                                new HistoryCustody(
                                        entry.copyAuthorityComplete(),
                                        entry.copiedIdentities(),
                                        entry.repositoryIds(),
                                        entry.sourceJobIds())))
                        .toList();
        boolean complete = !ids.isEmpty()
                && support.size() == ids.size()
                && authorized.size() == ids.size()
                && authorized.stream()
                        .map(AuthorizedSupport::resultId)
                        .collect(Collectors.toSet())
                        .containsAll(ids)
                && authorized.stream().allMatch(entry -> entry.custody().complete());
        var confirmed = copies.findByWorkspaceIdAndFeedbackIdOrderByPositionAsc(workspaceId, row.getId()).stream()
                .filter(copy -> copy.getPostedAt() != null
                        && (copy.getState() == FeedbackCopyState.POSTED
                                || copy.getState() == FeedbackCopyState.RETRACTED))
                .toList();
        boolean withdrawn = visibility.withdrawn(workspaceId, row.getId());
        boolean textAllowed = complete && !withdrawn && !basis.hasIncorrectBasis(workspaceId, row.getId());
        @Nullable String body = null;
        if (textAllowed) {
            if (row.getDestination() == FeedbackDestination.ON_WORK) {
                body = confirmed.stream()
                        .map(copy -> copy.getKind() == FeedbackCopyKind.SUMMARY ? row.getBody() : copy.getBody())
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("\n\n"));
                if (body.isEmpty()) body = null;
            } else if (row.getDestination() == FeedbackDestination.CONVERSATION && row.getDeliveredAt() != null) {
                body = confirmed.stream()
                        .filter(copy -> copy.getKind() == FeedbackCopyKind.CHAT_TURN)
                        .map(FeedbackCopy::getBody)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            } else if (row.getState() == FeedbackState.READY
                    || (row.getDestination() == FeedbackDestination.PRACTICE_PAGE && row.getDeliveredAt() != null)) {
                body = row.getBody();
            }
        }
        @Nullable
        FeedbackResponse response = row.getDeveloperId() == viewerAccountId
                ? responses.findCurrent(workspaceId, row.getId()).orElse(null)
                : null;
        @Nullable
        JsonNode publicHistory = row.getDestination() == FeedbackDestination.ON_WORK
                        && complete
                        && row.getDeliveredAt() != null
                        && !confirmed.isEmpty()
                ? publicHistory(row, support, authorized, confirmed, withdrawn, textAllowed)
                : null;
        return new FeedbackHistoryEntry(
                row.getId(),
                row.getReviewId(),
                row.getItemId(),
                row.getDeveloperId(),
                row.getDestination(),
                row.getState(),
                body,
                row.getCreatedAt(),
                row.getDeliveredAt(),
                ids,
                edges.stream()
                        .map(FeedbackBasis::getProblemId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList(),
                response == null ? null : response.getResolution(),
                response == null ? null : response.getUsefulness(),
                complete
                        ? authorized.stream()
                                .flatMap(entry -> entry.custody().sourceJobIds().stream())
                                .distinct()
                                .sorted(Comparator.comparing(UUID::toString))
                                .toList()
                        : List.of(),
                complete
                        ? authorized.stream()
                                .flatMap(entry -> entry.custody().copiedIdentities().stream())
                                .distinct()
                                .toList()
                        : List.of(),
                complete
                        ? authorized.stream()
                                .flatMap(entry -> entry.custody().repositoryIds().stream())
                                .distinct()
                                .sorted()
                                .toList()
                        : List.of(),
                queries.threadId(workspaceId, row.getId()),
                complete,
                publicHistory);
    }

    private record AuthorizedSupport(UUID resultId, String practiceSlug, HistoryCustody custody) {}

    private Optional<AuthorizedSupport> publicSupport(Result result, SourceUsePurpose purpose) {
        var facts = sourceAuthorization.custody(result);
        if (!sourceAuthorization.authorized(result, facts, purpose)) return Optional.empty();
        return practices
                .findById(result.getPracticeId())
                .filter(practice -> practice.getWorkspaceId() == result.getWorkspaceId())
                .map(practice -> new AuthorizedSupport(result.getId(), practice.getSlug(), facts));
    }

    private JsonNode publicHistory(
            Feedback row,
            List<Result> support,
            List<AuthorizedSupport> authorized,
            List<FeedbackCopy> confirmed,
            boolean withdrawn,
            boolean textAllowed) {
        var currentness = views.views(row.getWorkspaceId(), support).stream()
                .map(presentation::currentness)
                .toList();
        Currentness freshness = currentness.contains(Currentness.UNVERIFIABLE)
                ? Currentness.UNVERIFIABLE
                : currentness.contains(Currentness.STALE) ? Currentness.STALE : Currentness.CURRENT;
        var byId = authorized.stream().collect(Collectors.toMap(AuthorizedSupport::resultId, entry -> entry));
        @Nullable
        String summary =
                !textAllowed || confirmed.stream().noneMatch(copy -> copy.getKind() == FeedbackCopyKind.SUMMARY)
                        ? null
                        : row.getBody();
        var projection = new PublicEntry(
                row.getId(),
                row.getDestination(),
                row.getState(),
                Objects.requireNonNull(row.getDeliveredAt()),
                reviews.findByIdAndWorkspaceId(row.getReviewId(), row.getWorkspaceId())
                        .orElseThrow()
                        .getItemVersion(),
                summary,
                confirmed.stream().anyMatch(copy -> copy.getKind() == FeedbackCopyKind.SUMMARY)
                        ? row.getBody() == null
                        : row.getBody() != null,
                freshness,
                withdrawn,
                support.stream()
                        .map(result -> new PublicBasis(
                                result.getId(),
                                Objects.requireNonNull(byId.get(result.getId())).practiceSlug(),
                                result.getPracticeVersionId() == null
                                        ? null
                                        : result.getPracticeVersionId().toString(),
                                result.getOutcome()))
                        .toList(),
                confirmed.stream()
                        .map(copy -> new PublicCopy(
                                copy.getId(),
                                copy.getKind(),
                                !textAllowed
                                        ? null
                                        : copy.getKind() == FeedbackCopyKind.SUMMARY ? row.getBody() : copy.getBody(),
                                copy.getPath(),
                                copy.getStartLine(),
                                copy.getEndLine(),
                                copy.getSide(),
                                copy.getExternalRef(),
                                copy.getExternalUrl(),
                                Objects.requireNonNull(copy.getPostedAt()),
                                copy.getState()))
                        .toList());
        return mapper.valueToTree(projection);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private record PublicEntry(
            UUID id,
            FeedbackDestination destination,
            FeedbackState state,
            Instant deliveredAt,
            @Nullable String itemVersion,
            @Nullable String body,
            boolean bodyUnavailable,
            Currentness currentness,
            boolean withdrawn,
            List<PublicBasis> basedOn,
            List<PublicCopy> copies) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private record PublicBasis(
            UUID resultId, String practiceSlug, @Nullable String practiceVersionId, ResultOutcome outcome) {}

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private record PublicCopy(
            UUID id,
            FeedbackCopyKind kind,
            @Nullable String body,
            @Nullable String path,
            @Nullable Integer startLine,
            @Nullable Integer endLine,
            @Nullable CitationSide side,
            @Nullable String externalRef,
            @Nullable String externalUrl,
            Instant postedAt,
            FeedbackCopyState state) {}
}
