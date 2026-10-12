package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import de.tum.cit.aet.hephaestus.agent.context.providers.mentor.ConversationConsentGate;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackEvidenceRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacement;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackPlacementRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.PlacementType;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class ConversationalDeliveryReconciler {

    private static final Logger log = LoggerFactory.getLogger(ConversationalDeliveryReconciler.class);

    private final FeedbackRepository feedbackRepository;
    private final FeedbackEvidenceRepository feedbackEvidenceRepository;
    private final FeedbackPlacementRepository feedbackPlacementRepository;
    private final PracticeResultRepository practiceResultRepository;
    private final ResultVisibilityPolicy visibilityPolicy;
    private final ConversationConsentGate consentGate;

    public ConversationalDeliveryReconciler(
            FeedbackRepository feedbackRepository,
            FeedbackEvidenceRepository feedbackEvidenceRepository,
            FeedbackPlacementRepository feedbackPlacementRepository,
            PracticeResultRepository practiceResultRepository,
            ResultVisibilityPolicy visibilityPolicy,
            ConversationConsentGate consentGate) {
        this.feedbackRepository = feedbackRepository;
        this.feedbackEvidenceRepository = feedbackEvidenceRepository;
        this.feedbackPlacementRepository = feedbackPlacementRepository;
        this.practiceResultRepository = practiceResultRepository;
        this.visibilityPolicy = visibilityPolicy;
        this.consentGate = consentGate;
    }

    /**
     * Whether the mentor may show {@code recipientUserId} feedback about this result now: one {@link #admitted}
     * would act on, and about the recipient. Asked while the turn runs, before the link is acknowledged, shown or
     * stored, so an id the model invented or borrowed never reaches the developer; {@link #reconcile} still settles
     * the ledger once the reply is stored.
     */
    @Transactional(readOnly = true)
    public boolean admits(long workspaceId, long recipientUserId, UUID practiceResultId) {
        PracticeResult practiceResult =
                admitted(workspaceId, List.of(practiceResultId)).get(practiceResultId);
        return practiceResult != null && Long.valueOf(recipientUserId).equals(practiceResult.getAboutUserId());
    }

    public int reconcile(long workspaceId, long recipientUserId, UUID chatMessageId, List<UUID> linkedResultIds) {
        Instant now = Instant.now();
        for (PracticeResult practiceResult :
                admitted(workspaceId, linkedResultIds).values()) {
            UUID practiceResultId = practiceResult.getId();
            List<UUID> feedbackIds = feedbackEvidenceRepository.findPreparedConversationFeedbackIdsByResult(
                    workspaceId, recipientUserId, practiceResultId);
            if (feedbackIds.isEmpty()) {
                continue;
            }
            if (feedbackRepository.existsDeliveredInContextForResult(workspaceId, recipientUserId, practiceResultId)) {
                continue;
            }
            UUID feedbackId = feedbackIds.get(0);
            int flipped = feedbackRepository.markConversationDelivered(feedbackId, now);
            if (flipped == 1) {
                Feedback unit = feedbackRepository.getReferenceById(feedbackId);
                feedbackPlacementRepository.save(FeedbackPlacement.builder()
                        .feedback(unit)
                        .placementType(PlacementType.CONVERSATION_TURN)
                        .chatMessageId(chatMessageId)
                        .createdAt(now)
                        .build());
                log.info(
                        "Conversational feedback delivered: feedbackId={}, chatMessageId={}, recipient={}",
                        feedbackId,
                        chatMessageId,
                        recipientUserId);
                return 1;
            }
        }
        return 0;
    }

    /**
     * The linked results this turn may act on: the results this workspace can read whose claim and
     * evidence the visibility policy still permits for mentoring and, for one drawn from a conversation, whose
     * conversation is still consented to, keyed by id, in the mentor's emission order. Pausing a channel erases
     * nothing, so its consent is read here, not inferred from the evidence.
     *
     * <p>{@code linkedResultIds} are the links the stored reply shows. {@link #admits} already checked each one
     * while the turn ran, but authorization can change between that check and the end of the turn, so the ledger is
     * gated again here, and <em>both</em> endings of a turn are ledger writes — one flips a unit to DELIVERED, the
     * other burns it to SUPPRESSED — so both are gated here rather than at one call site.
     *
     * <p>A refused id is left alone rather than settled. Refusal is not always terminal (an evidence
     * authorization the source catalog withdrew can come back; a claim measured against superseded review
     * rules cannot), and nothing ever writes a unit back to PREPARED, so settling on the first refusal would
     * spend the developer's coaching on a condition that may lift tomorrow. The unit behind a refused id is
     * still settled, by {@link ConversationFeedbackTtlSweeper} at the end of its window.
     *
     * <p>Each check is one batch for the whole turn, not one query per linked id — nothing caps how many
     * results a mentor turn links (the reply stores a part per {@code link_observation} tool call).
     */
    private Map<UUID, PracticeResult> admitted(long workspaceId, List<UUID> linkedResultIds) {
        if (linkedResultIds == null || linkedResultIds.isEmpty()) {
            return Map.of();
        }
        // Emission order, deduplicated: the first linked result that survives every gate wins the turn, so
        // the order the mentor linked them in is part of the answer and must survive the batching below.
        Set<UUID> practiceResultIds = new LinkedHashSet<>(linkedResultIds);
        List<PracticeResult> rows =
                practiceResultRepository.findAllByIdInAndWorkspaceId(practiceResultIds, workspaceId);
        Map<UUID, PracticeResult> byId = new HashMap<>(rows.size());
        for (PracticeResult practiceResult : rows) {
            byId.put(practiceResult.getId(), practiceResult);
        }
        Set<UUID> visible = visibilityPolicy.permitsForNewDelivery(
                workspaceId, byId.values(), SourceUsePurpose.CONVERSATIONAL_MENTORING);
        Set<Long> consentedThreads = consentGate.activeThreadIds(
                workspaceId,
                byId.values().stream()
                        .filter(ConversationalDeliveryReconciler::fromConversation)
                        .map(PracticeResult::getArtifactId)
                        .toList());
        Map<UUID, PracticeResult> admitted = new LinkedHashMap<>();
        for (UUID practiceResultId : practiceResultIds) {
            PracticeResult practiceResult = byId.get(practiceResultId);
            // Absent from any batch means refused.
            if (practiceResult != null
                    && visible.contains(practiceResultId)
                    && (!fromConversation(practiceResult)
                            || consentedThreads.contains(practiceResult.getArtifactId()))) {
                admitted.put(practiceResultId, practiceResult);
            }
        }
        return admitted;
    }

    private static boolean fromConversation(PracticeResult practiceResult) {
        return ArtifactKinds.CONVERSATION_THREAD.equals(practiceResult.getArtifactKind());
    }

    /**
     * Silent Mode permanently suppresses the unit instead of postponing it: the mentor had this to say and
     * the instance stopped it, which is a different answer to "why was nothing said" than "it is still
     * queued". Walks the same {@link #admitted} results {@link #reconcile} may act on: a linked id that
     * subsystem is not allowed to raise is not one Silent Mode gets to claim it stopped. It does not repeat
     * that method's exact-practiceResult delivery check — "already said inline" is about what to say next, and nothing is
     * being said.
     */
    public int suppressForSilentMode(long workspaceId, long recipientUserId, List<UUID> linkedResultIds) {
        for (UUID practiceResultId : admitted(workspaceId, linkedResultIds).keySet()) {
            List<UUID> feedbackIds = feedbackEvidenceRepository.findPreparedConversationFeedbackIdsByResult(
                    workspaceId, recipientUserId, practiceResultId);
            if (feedbackIds.isEmpty()) {
                continue;
            }
            UUID feedbackId = feedbackIds.get(0);
            if (feedbackRepository.markConversationSuppressedBySilentMode(feedbackId) == 1) {
                log.info(
                        "Conversational feedback suppressed by instance Silent Mode: feedbackId={}, recipient={}",
                        feedbackId,
                        recipientUserId);
                return 1;
            }
        }
        return 0;
    }
}
