package de.tum.cit.aet.hephaestus.practices.feedback.delivery;

import de.tum.cit.aet.hephaestus.integration.core.spi.AboutRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.integration.core.spi.CopyCapability;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemCatalog;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.DeveloperTextSanitizer;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackBasis;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackBasisRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSupersession;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ProblemRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.ConversationDecision;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackBasisInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDraft;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStopReason;
import de.tum.cit.aet.hephaestus.practices.spi.PracticePageDecision;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewSnapshotQuery;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
@ConditionalOnCaptureRole
public class FeedbackPreparationService {
    private final ReviewSnapshotQuery reviews;
    private final ResultRepository results;
    private final ProblemRepository problems;
    private final FeedbackBasisRepository basis;
    private final ItemCatalog catalog;
    private final Clock clock;
    private final PracticeVersionRepository versions;
    private final RepeatedSummaryCheck repeated;
    private final FeedbackResponseSuppressionFilter suppression;
    private final FeedbackSupersession replacements;
    private final ApplicationEventPublisher events;
    private final PracticePageSupportReader pageSupport;
    private final FeedbackCompositionContextService composition;

    public record Preparation(FeedbackDraft draft, @Nullable UUID replacesId) {}

    public Preparation prepare(FeedbackDraft draft, Optional<Feedback> existing) {
        if ((draft.body() != null && draft.body().length() > 8000)
                || draft.copies().stream()
                                .filter(copy -> copy.kind() == FeedbackCopyKind.LINE_NOTE
                                        || copy.kind() == FeedbackCopyKind.FILE_NOTE)
                                .count()
                        > 30
                || draft.copies().stream()
                        .anyMatch(copy -> copy.body() != null && copy.body().length() > 2000))
            throw new IllegalArgumentException("Composition exceeds its text bounds.");
        var snapshot = reviews.find(draft.workspaceId(), draft.reviewId()).orElseThrow();
        var descriptor = catalog.descriptorFor(snapshot.item().kind()).orElseThrow();
        var ids = draft.basis().stream()
                .map(FeedbackBasisInput::resultId)
                .distinct()
                .toList();
        var support = results.findByWorkspaceIdAndIdIn(draft.workspaceId(), ids);
        if (support.size() != ids.size()
                || support.stream()
                        .anyMatch(row -> !Objects.equals(row.getDeveloperId(), draft.developerId())
                                || (draft.destination() != FeedbackDestination.PRACTICE_PAGE
                                        && !row.getReviewId().equals(draft.reviewId()))))
            throw conflict("Composition must use this review's admitted results for one developer.");
        var primary = support.stream()
                .filter(row -> row.getReviewId().equals(draft.reviewId()))
                .findFirst()
                .orElseThrow();
        String body = clean(draft.body());
        FeedbackStopReason reason = draft.stopReason();
        UUID replacesId = null;
        if (draft.destination() == FeedbackDestination.PRACTICE_PAGE) {
            if (support.stream().anyMatch(row -> row.getPracticeId() != primary.getPracticeId()))
                throw conflict("A practice-page piece must address one practice.");
            if (existing.isPresent()) {
                var retainedIds =
                        basis
                                .findByWorkspaceIdAndFeedbackIdOrderByOrdinalAscIdAsc(draft.workspaceId(), draft.id())
                                .stream()
                                .map(FeedbackBasis::getResultId)
                                .distinct()
                                .toList();
                if (support.stream()
                        .anyMatch(row ->
                                !row.getReviewId().equals(draft.reviewId()) && !retainedIds.contains(row.getId())))
                    throw conflict("A replay must use its retained historical basis.");
                var retainedRows = results.findByWorkspaceIdAndIdIn(draft.workspaceId(), retainedIds).stream()
                        .collect(Collectors.toMap(Result::getId, row -> row));
                support = retainedIds.stream()
                        .map(retainedRows::get)
                        .filter(Objects::nonNull)
                        .toList();
            } else {
                var selection = pageSupport.select(
                        draft.workspaceId(), draft.developerId(), primary.getPracticeId(), clock.instant());
                var authorizedIds =
                        selection.evidence().stream().map(Result::getId).toList();
                if (support.stream()
                        .anyMatch(row ->
                                !row.getReviewId().equals(draft.reviewId()) && !authorizedIds.contains(row.getId())))
                    throw conflict("Historical support must come from the authorized practice-page read.");
                support = selection.evidence();
                var prior = selection.previous();
                var decision = composition.pageDecision(primary, support, selection, clock.instant());
                if (decision != PracticePageDecision.ADMIT || ids.stream().anyMatch(id -> !authorizedIds.contains(id)))
                    reason = decision == PracticePageDecision.PRACTICE_REQUIRES_APPROVAL
                            ? FeedbackStopReason.PRACTICE_REQUIRES_APPROVAL
                            : FeedbackStopReason.NOTHING_NEW;
                if (prior.isPresent() && !prior.get().id().equals(draft.id())) {
                    if (prior.get().isOpen()) replacesId = prior.get().id();
                    else if (support.stream()
                            .noneMatch(row ->
                                    row.getRecordedAt().isAfter(prior.get().nextEvidenceSince(Instant.EPOCH))))
                        reason = FeedbackStopReason.NOTHING_NEW;
                }
            }
        }
        if (draft.destination() == FeedbackDestination.CONVERSATION && existing.isEmpty()) {
            for (var result : support) {
                var decision = composition.conversationDecision(snapshot, result);
                if (decision != ConversationDecision.ADMIT) {
                    reason = switch (decision) {
                        case BACKFILL_QUIET -> FeedbackStopReason.BACKFILL_QUIET;
                        case PRACTICE_REQUIRES_APPROVAL -> FeedbackStopReason.PRACTICE_REQUIRES_APPROVAL;
                        case HAS_INLINE_ANCHOR, ALREADY_DELIVERED_ON_WORK -> FeedbackStopReason.NOTHING_NEW;
                        case REVIEWER_DEFERRED -> FeedbackStopReason.PUBLIC_SUBJECT_INELIGIBLE;
                        case MENTOR_UNAVAILABLE, NOT_DELIVERABLE -> FeedbackStopReason.RECIPIENT_UNAVAILABLE;
                        case ADMIT -> throw new IllegalStateException("An admitted result has no refusal.");
                    };
                    break;
                }
            }
        }
        if (draft.destination() == FeedbackDestination.ON_WORK) {
            if (support.stream()
                    .anyMatch(row -> row.getPracticeVersionId() == null
                            || row.getEvidenceVersion() != 1
                            || versions.findById(row.getPracticeVersionId())
                                    .filter(version -> version.getWorkspaceId() == draft.workspaceId()
                                            && AboutRole.AUTHOR
                                                    .name()
                                                    .equals(version.getDefinition()
                                                            .path("about")
                                                            .asString()))
                                    .isEmpty())) reason = FeedbackStopReason.PUBLIC_SUBJECT_INELIGIBLE;
            if (body != null
                    && repeated.repeats(draft.workspaceId(), snapshot.item().itemId(), draft.developerId(), body))
                reason = FeedbackStopReason.REPEATS_DELIVERED_NOTE;
            if (!descriptor.copyCapabilities().contains(CopyCapability.SUMMARY)
                    && !descriptor.copyCapabilities().contains(CopyCapability.LINE_NOTE))
                reason = FeedbackStopReason.RECIPIENT_UNAVAILABLE;
        }
        var suppressed = suppression.suppressedProblems(
                draft.workspaceId(),
                problems
                        .findByWorkspaceIdAndResultIdInOrderByResultIdAscPositionAsc(
                                draft.workspaceId(),
                                support.stream().map(Result::getId).toList())
                        .stream()
                        .map(row -> row.getId())
                        .toList());
        if (!suppressed.isEmpty())
            reason = suppressed.containsValue(FeedbackStopReason.REACTED_DISAGREE)
                    ? FeedbackStopReason.REACTED_DISAGREE
                    : FeedbackStopReason.REACTED_NOT_APPLICABLE;
        List<FeedbackBasisInput> edges = new ArrayList<>(draft.basis());
        int ordinal = edges.stream().mapToInt(FeedbackBasisInput::ordinal).max().orElse(-1) + 1;
        for (var row : support)
            if (!ids.contains(row.getId())) edges.add(new FeedbackBasisInput(row.getId(), null, ordinal++));
        List<FeedbackCopyInput> planned = new ArrayList<>();
        for (var copy : draft.copies()) {
            if ((copy.kind() == FeedbackCopyKind.LINE_NOTE || copy.kind() == FeedbackCopyKind.FILE_NOTE)
                    && !descriptor.copyCapabilities().contains(CopyCapability.LINE_NOTE))
                throw conflict("This item does not support anchored feedback.");
            String words = clean(copy.body());
            if (copy.kind() != FeedbackCopyKind.SUMMARY && copy.kind() != FeedbackCopyKind.CHAT_TURN && words == null)
                continue;
            if (copy.kind() == FeedbackCopyKind.SUMMARY && body == null) continue;
            planned.add(new FeedbackCopyInput(
                    copy.id(),
                    copy.kind(),
                    copy.position(),
                    words,
                    copy.path(),
                    copy.startLine(),
                    copy.endLine(),
                    copy.side(),
                    copy.writeKey(),
                    copy.writeMarker()));
        }
        if (reason == null && body == null && planned.stream().noneMatch(copy -> copy.body() != null))
            reason = FeedbackStopReason.EMPTY_AFTER_SANITIZE;
        return new Preparation(
                new FeedbackDraft(
                        draft.id(),
                        draft.workspaceId(),
                        draft.reviewId(),
                        draft.destination() == FeedbackDestination.PRACTICE_PAGE
                                ? null
                                : snapshot.item().itemId(),
                        draft.developerId(),
                        draft.destination(),
                        reason == null ? draft.state() : FeedbackState.WITHHELD,
                        reason,
                        body,
                        edges,
                        planned,
                        draft.checks()),
                replacesId);
    }

    public void recorded(Preparation preparation, Feedback row) {
        if (preparation.replacesId() != null)
            replacements.replaceOpen(row.getWorkspaceId(), preparation.replacesId(), row.getId());
        if (row.getDestination() == FeedbackDestination.CONVERSATION && row.getState() == FeedbackState.READY)
            events.publishEvent(new ConversationFeedbackPreparedEvent(row.getWorkspaceId(), row.getDeveloperId(), 1));
    }

    private static @Nullable String clean(@Nullable String text) {
        if (text == null) return null;
        var sanitized = DeveloperTextSanitizer.sanitize(text);
        return sanitized.isBlank() ? null : sanitized;
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
