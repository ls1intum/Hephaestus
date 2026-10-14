package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.context.HistoryCopies;
import de.tum.cit.aet.hephaestus.agent.context.HistoryCopy;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJob;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.ConversationEligibilityDTO;
import de.tum.cit.aet.hephaestus.agent.proxy.dto.PracticePageSupportDTO;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.spi.ConditionalOnCaptureRole;
import de.tum.cit.aet.hephaestus.practices.spi.AdmittedResultSnapshot;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackCompositionContext;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackStore;
import de.tum.cit.aet.hephaestus.practices.spi.PracticePageSupport;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryEntry;
import de.tum.cit.aet.hephaestus.practices.spi.ResultHistoryQuery;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Returns no historical words until their exact retained identity and copy authority have been checked. */
@Component
@ConditionalOnCaptureRole
@RequiredArgsConstructor
public class FeedbackCompositionContextReader {
    private final FeedbackStore feedback;
    private final ResultHistoryQuery history;
    private final HistoryCopies copies;

    public Read read(ReviewJob job, List<AdmittedResultSnapshot> admitted) {
        var people = admitted.stream()
                .map(row -> row.candidate().developerId())
                .distinct()
                .toList();
        if (people.size() != 1 || people.getFirst() == null)
            return new Read(new FeedbackCompositionContext(null, null), List.of(), List.of());
        long developer = Objects.requireNonNull(people.getFirst());
        var context = feedback.compositionContext(
                job.getWorkspaceId(),
                job.getReviewId(),
                admitted.stream().map(AdmittedResultSnapshot::id).toList());
        var support = context.practicePageSupport();
        var retained = new ArrayList<ResultHistoryEntry>();
        if (support != null && support.state() == PracticePageSupport.State.COMPLETE) {
            for (var practice : support.practices())
                for (var occurrence : practice.occurrences()) {
                    var result = history.find(
                                    job.getWorkspaceId(),
                                    occurrence.resultId(),
                                    developer,
                                    SourceUsePurpose.AUTOMATED_PRACTICE_REVIEW)
                            .orElse(null);
                    if (result == null
                            || !copies.of(result).complete()
                            || !Long.valueOf(developer).equals(result.developerAccountId())
                            || result.outcome() != ResultOutcome.NOT_MET
                            || !practice.practiceSlug().equals(result.practiceSlug())
                            || !occurrence.itemId().equals(Long.toString(result.itemId()))
                            || !occurrence.itemKind().equals(result.itemKind())
                            || !Objects.equals(occurrence.summary(), result.summary())
                            || !Objects.equals(occurrence.explanation(), result.explanation())
                            || !Objects.equals(occurrence.evidence(), result.evidence())
                            || !occurrence.recordedAt().equals(result.recordedAt()))
                        return new Read(
                                new FeedbackCompositionContext(
                                        new PracticePageSupport(
                                                PracticePageSupport.State.REFUSED,
                                                support.readAt(),
                                                List.of(),
                                                "Copy authority for historical support is unavailable"),
                                        context.conversationEligibility()),
                                List.of(),
                                List.of());
                    if (retained.stream().noneMatch(entry -> entry.resultId().equals(result.resultId())))
                        retained.add(result);
                }
        }
        return new Read(context, retained.stream().map(copies::of).toList(), retained);
    }

    public Read readForCapture(ReviewJob job, List<AdmittedResultSnapshot> admitted) {
        var read = read(job, admitted);
        if (read.copies().stream().anyMatch(copy -> !copies.record(copy)))
            throw new IllegalStateException("Historical support cannot be copied into this capture");
        return read;
    }

    public static @Nullable PracticePageSupportDTO page(Read read) {
        var support = read.context().practicePageSupport();
        if (support == null) return null;
        if (support.state() != PracticePageSupport.State.COMPLETE)
            return new PracticePageSupportDTO.Closed(support.state(), support.readAt(), support.refusal());
        return new PracticePageSupportDTO.Complete(
                support.state(),
                support.readAt(),
                support.practices().stream()
                        .map(practice -> new PracticePageSupportDTO.PracticeSupportDTO(
                                practice.practiceSlug(),
                                practice.decision(),
                                practice.occurrences().stream()
                                        .map(row -> new PracticePageSupportDTO.SupportOccurrenceDTO(
                                                row.resultId(),
                                                row.itemKind(),
                                                row.itemId(),
                                                row.outcome(),
                                                row.origin(),
                                                row.summary(),
                                                row.explanation(),
                                                row.evidence(),
                                                row.practiceVersionId(),
                                                row.recordedAt()))
                                        .toList()))
                        .toList());
    }

    public static @Nullable ConversationEligibilityDTO conversation(Read read) {
        var eligibility = read.context().conversationEligibility();
        return eligibility == null
                ? null
                : new ConversationEligibilityDTO(
                        eligibility.state(),
                        eligibility.readAt(),
                        eligibility.results().stream()
                                .map(row -> new ConversationEligibilityDTO.ResultDecisionDTO(
                                        row.resultId(), row.decision()))
                                .toList());
    }

    public record Read(FeedbackCompositionContext context, List<HistoryCopy> copies, List<ResultHistoryEntry> support) {
        public Read {
            copies = List.copyOf(copies);
            support = List.copyOf(support);
        }
    }
}
