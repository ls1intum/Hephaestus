package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedback;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.integration.core.spi.FeedbackAnchor;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedResultSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackBasisInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyInput;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCopyKind;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDestination;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackDraft;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackIds;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/** Allocates drafts only from this attempt's admitted results and verified anchors. */
public final class FeedbackDraftBuilder {
    private FeedbackDraftBuilder() {}

    public static List<FeedbackDraft> build(
            ReviewJob job,
            Set<UUID> allowedResults,
            List<AdmittedResultSnapshot> admitted,
            List<ResultHistoryEntry> historical,
            List<ComposedFeedback> composition,
            Map<UUID, FeedbackAnchor> anchors) {
        var byId = admitted.stream().collect(Collectors.toMap(AdmittedResultSnapshot::id, Function.identity()));
        var historyById =
                historical.stream().collect(Collectors.toMap(ResultHistoryEntry::resultId, Function.identity()));
        var drafts = new ArrayList<FeedbackDraft>();
        for (var piece : composition) {
            if (!allowedResults.containsAll(piece.resultIds()))
                throw new ResultAdmissionService.AdmissionConflictException();
            var support = piece.resultIds().stream()
                    .filter(byId::containsKey)
                    .map(id -> Objects.requireNonNull(byId.get(id)))
                    .toList();
            if (support.isEmpty()) throw new ResultAdmissionService.AdmissionConflictException();
            var prior = piece.resultIds().stream()
                    .filter(id -> !byId.containsKey(id))
                    .map(id -> Objects.requireNonNull(historyById.get(id)))
                    .toList();
            if ((!prior.isEmpty() && piece.destination() != FeedbackDestination.PRACTICE_PAGE)
                    || (!piece.notes().isEmpty() && piece.destination() != FeedbackDestination.ON_WORK))
                throw new ResultAdmissionService.AdmissionConflictException();
            Long developer = support.getFirst().candidate().developerId();
            if (developer == null
                    || support.stream()
                            .anyMatch(row -> !developer.equals(row.candidate().developerId()))) {
                throw new ResultAdmissionService.AdmissionConflictException();
            }
            if (prior.stream()
                            .anyMatch(row -> !developer.equals(row.developerAccountId())
                                    || row.practiceId()
                                            != support.getFirst().candidate().practiceId())
                    || (piece.destination() == FeedbackDestination.PRACTICE_PAGE
                            && support.stream()
                                    .anyMatch(row -> row.candidate().practiceId()
                                            != support.getFirst().candidate().practiceId())))
                throw new ResultAdmissionService.AdmissionConflictException();
            UUID first = support.getFirst().id();
            UUID id = piece.withheldReason() != null
                    ? FeedbackIds.withheld(
                            job.getWorkspaceId(), job.getReviewId(), developer, piece.destination(), first)
                    : switch (piece.destination()) {
                        case ON_WORK -> FeedbackIds.onWork(job.getWorkspaceId(), job.getReviewId(), developer);
                        case PRACTICE_PAGE ->
                            FeedbackIds.practicePage(job.getWorkspaceId(), job.getReviewId(), developer, first);
                        case CONVERSATION ->
                            FeedbackIds.conversation(
                                    job.getWorkspaceId(), job.getReviewId(), developer, piece.resultIds());
                    };
            var basis = new ArrayList<FeedbackBasisInput>();
            for (var resultId : piece.resultIds()) basis.add(new FeedbackBasisInput(resultId, null, basis.size()));
            var copies = new ArrayList<FeedbackCopyInput>();
            if (piece.destination() == FeedbackDestination.CONVERSATION) {
                copies.add(copy(id, FeedbackCopyKind.CHAT_TURN, 0, null, null));
            } else if (piece.destination() == FeedbackDestination.ON_WORK) {
                if (piece.summary() != null) copies.add(copy(id, FeedbackCopyKind.SUMMARY, 0, null, null));
                for (var note : piece.notes()) {
                    var owner = support.stream()
                            .filter(row -> row.problems().stream()
                                    .anyMatch(problem -> problem.id().equals(note.problemId())))
                            .findFirst()
                            .orElseThrow(ResultAdmissionService.AdmissionConflictException::new);
                    var anchor = anchors.get(note.problemId());
                    if (anchor == null) throw new ResultAdmissionService.AdmissionConflictException();
                    basis.add(new FeedbackBasisInput(owner.id(), note.problemId(), basis.size()));
                    copies.add(copy(id, FeedbackCopyKind.LINE_NOTE, copies.size(), note.body(), anchor));
                }
            }
            if (drafts.stream().anyMatch(draft -> draft.id().equals(id)))
                throw new ResultAdmissionService.AdmissionConflictException();
            drafts.add(new FeedbackDraft(
                    id,
                    job.getWorkspaceId(),
                    job.getReviewId(),
                    piece.destination() == FeedbackDestination.PRACTICE_PAGE
                            ? null
                            : job.getInput().itemId(),
                    developer,
                    piece.destination(),
                    piece.withheldReason() == null ? FeedbackState.READY : FeedbackState.WITHHELD,
                    piece.withheldReason(),
                    piece.summary(),
                    basis,
                    copies,
                    Map.of()));
        }
        return List.copyOf(drafts);
    }

    private static FeedbackCopyInput copy(
            UUID feedbackId,
            FeedbackCopyKind kind,
            int position,
            @Nullable String body,
            @Nullable FeedbackAnchor anchor) {
        UUID id = FeedbackIds.copy(feedbackId, kind, position);
        return new FeedbackCopyInput(
                id,
                kind,
                position,
                body,
                anchor == null ? null : anchor.path(),
                anchor == null ? null : anchor.startLine(),
                anchor == null ? null : anchor.endLine(),
                anchor == null ? null : anchor.side(),
                id.toString(),
                kind == FeedbackCopyKind.CHAT_TURN ? null : FeedbackIds.writeMarker(id));
    }
}
