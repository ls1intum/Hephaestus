package de.tum.cit.aet.hephaestus.agent.handler.inapp;

import de.tum.cit.aet.hephaestus.agent.handler.FeedbackLedgerRecorder;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackResolution;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackThreadKey;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackUsefulness;
import de.tum.cit.aet.hephaestus.practices.feedback.InAppFeedbackBody;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.reaction.Reaction;
import de.tum.cit.aet.hephaestus.practices.result.reaction.ReactionRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dev-only: writes in-app feedback with a history (prepared weeks ago, read, answered), which
 * {@link InAppFeedbackPreparer} cannot, since it writes a card as a review prepares it. The rows use the
 * preparer's body layout ({@link InAppFeedbackBody}) and thread key ({@link FeedbackThreadKey}). The caller chooses
 * every id, so it can find and remove what it wrote.
 */
@Service
@ConditionalOnServerRole
@ConditionalOnBooleanProperty("hephaestus.dev.seed-enabled")
@RequiredArgsConstructor
public class DevInAppFeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackEvidenceRepository feedbackEvidenceRepository;
    private final PracticeResultRepository practiceResultRepository;
    private final ReactionRepository reactionRepository;

    /**
     * One card as the developer's page would hold it.
     *
     * @param agentJobId the review whose cycle prepared the card
     * @param evidence the problem results the card stands on, in the order the card cites them; each must
     *     be about the recipient and on the card's practice
     * @param deliveredAt when the recipient first opened it; {@code null} for a card still unread
     * @param response the recipient's answer; only on a card they opened
     */
    public record Card(
            UUID id,
            UUID agentJobId,
            Long recipientUserId,
            String practiceSlug,
            String headline,
            String message,
            String nextStep,
            Instant createdAt,
            @Nullable Instant deliveredAt,
            List<UUID> evidence,
            @Nullable Response response) {}

    /** The recipient's answer, as one response row holds it. */
    public record Response(
            UUID id,
            Instant at,
            @Nullable FeedbackUsefulness usefulness,
            @Nullable FeedbackResolution resolution,
            @Nullable String explanation) {}

    /**
     * Writes the cards, their evidence and their answers in one transaction. Cards of one job take the next
     * positions in the job's in-app band, in the order given.
     *
     * @return the ids of the cards written
     */
    @Transactional
    public List<UUID> write(Long workspaceId, List<Card> cards) {
        Map<UUID, PracticeResult> evidence = practiceResultRepository
                .findAllByIdInAndWorkspaceId(
                        cards.stream().flatMap(card -> card.evidence().stream()).toList(), workspaceId)
                .stream()
                .collect(Collectors.toMap(PracticeResult::getId, Function.identity()));
        // Every card is checked before any is written, so a refused request leaves nothing behind.
        for (Card card : cards) {
            check(card, evidence);
        }
        Map<UUID, Integer> positions = new HashMap<>();
        for (Card card : cards) {
            int position = positions.merge(card.agentJobId(), 1, Integer::sum) - 1;
            Feedback feedback = feedbackRepository.save(Feedback.builder()
                    .id(card.id())
                    .agentJobId(card.agentJobId())
                    .workspaceId(workspaceId)
                    .recipientUserId(card.recipientUserId())
                    .aboutUserId(card.recipientUserId())
                    .channel(FeedbackChannel.IN_APP)
                    .position(FeedbackLedgerRecorder.IN_APP_UNIT_ORDINAL_BASE + position)
                    .deliveryState(
                            card.deliveredAt() == null
                                    ? FeedbackDeliveryState.PREPARED
                                    : FeedbackDeliveryState.DELIVERED)
                    .source(FeedbackSource.AGENT)
                    .body(InAppFeedbackBody.render(card.headline(), card.message(), card.nextStep()))
                    .threadKey(FeedbackThreadKey.forPractice(
                            card.practiceSlug(), card.recipientUserId(), FeedbackChannel.IN_APP))
                    .proposedPracticeSlugs(List.of(card.practiceSlug()))
                    .createdAt(card.createdAt())
                    .deliveredAt(card.deliveredAt())
                    .build());
            int ordinal = 0;
            for (UUID practiceResultId : card.evidence()) {
                feedbackEvidenceRepository.insertIfAbsent(
                        feedback.getId(), practiceResultId, null, EvidenceRole.PRIMARY.name(), ordinal++);
            }
            Response response = card.response();
            if (response != null) {
                reactionRepository.save(Reaction.builder()
                        .id(response.id())
                        .feedback(feedback)
                        .reactorUserId(card.recipientUserId())
                        .usefulness(response.usefulness())
                        .resolution(response.resolution())
                        .explanation(response.explanation())
                        .createdAt(response.at())
                        .build());
            }
        }
        return cards.stream().map(Card::id).toList();
    }

    /** A card the page could not have: evidence from elsewhere, or an answer to a card nobody opened. */
    private static void check(Card card, Map<UUID, PracticeResult> evidence) {
        if (card.evidence().isEmpty()) {
            throw invalid(card, "cites no result");
        }
        for (UUID practiceResultId : card.evidence()) {
            PracticeResult practiceResult = evidence.get(practiceResultId);
            if (practiceResult == null) {
                throw invalid(card, "cites result " + practiceResultId + ", which is not in this workspace");
            }
            if (!card.recipientUserId().equals(practiceResult.getAboutUserId())
                    || !card.practiceSlug().equals(practiceResult.getPractice().getSlug())) {
                throw invalid(card, "cites result " + practiceResultId + ", which is about other work");
            }
        }
        if (card.response() != null && card.deliveredAt() == null) {
            throw invalid(card, "has an answer but was never opened");
        }
    }

    private static ResponseStatusException invalid(Card card, String reason) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Card " + card.id() + " " + reason);
    }
}
