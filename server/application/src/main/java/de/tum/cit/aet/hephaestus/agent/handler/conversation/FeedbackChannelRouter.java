package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import de.tum.cit.aet.hephaestus.practices.PracticePreconditionClause;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomyPolicy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.result.PracticeResultRepository;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaults;
import de.tum.cit.aet.hephaestus.practices.review.WorkspaceReviewDefaultsProvider;
import de.tum.cit.aet.hephaestus.practices.review.autonomy.AutonomyResolver;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Decides which of a cycle's results are eligible for conversational delivery. A result is
 * {@link ConversationRoutingDecision#ADMIT admitted} to the IN_CHAT channel iff ALL of: its provenance admits the
 * conversation channel, its practice autonomy admits the conversation channel, author-targeted, a
 * negative outcome, has no natural inline anchor, and is not already bound to a
 * DELIVERED IN_CONTEXT unit for the same recipient. Every other case is a named, testable non-admission reason.
 *
 * <p>Pure routing - it reads the feedback ledger but writes nothing. The {@link ConversationalFeedbackPreparer}
 * turns the admitted set into PREPARED units.
 */
@Component
public class FeedbackChannelRouter {

    private final FeedbackRepository feedbackRepository;
    private final PracticeResultRepository practiceResultRepository;
    private final WorkspaceReviewDefaultsProvider workspaceDefaults;

    public FeedbackChannelRouter(
            FeedbackRepository feedbackRepository,
            PracticeResultRepository practiceResultRepository,
            WorkspaceReviewDefaultsProvider workspaceDefaults) {
        this.feedbackRepository = feedbackRepository;
        this.practiceResultRepository = practiceResultRepository;
        this.workspaceDefaults = workspaceDefaults;
    }

    public List<PracticeResult> admit(List<PracticeResult> practiceResults, long workspaceId, RoutingContext context) {
        Map<UUID, ConversationRoutingDecision> decisions = decide(practiceResults, workspaceId, context);
        return practiceResults.stream()
                .filter(practiceResult -> decisions.get(practiceResult.getId()) == ConversationRoutingDecision.ADMIT)
                .toList();
    }

    /** Each result's routing decision, by its id, in the order given. */
    public Map<UUID, ConversationRoutingDecision> decide(
            List<PracticeResult> practiceResults, long workspaceId, RoutingContext context) {
        WorkspaceReviewDefaults defaults = workspaceDefaults.forWorkspace(workspaceId);
        Map<UUID, PracticeAutonomy> autonomyByPracticeId =
                autonomyByPracticeId(practiceResults, workspaceId, defaults.defaultAutonomy());
        Map<UUID, ConversationRoutingDecision> decisions = new LinkedHashMap<>();
        for (PracticeResult practiceResult : practiceResults) {
            decisions.put(
                    practiceResult.getId(),
                    route(practiceResult, autonomyByPracticeId.get(practiceResult.getId()), workspaceId, context));
        }
        return decisions;
    }

    public ConversationRoutingDecision route(
            PracticeResult practiceResult,
            @Nullable PracticeAutonomy autonomy,
            long workspaceId,
            RoutingContext context) {
        if (!PracticeAutonomyPolicy.delivers(practiceResult.getOrigin(), autonomy, FeedbackChannel.IN_CHAT)) {
            return practiceResult.getOrigin().delivers(FeedbackChannel.IN_CHAT)
                    ? ConversationRoutingDecision.PRACTICE_REQUIRES_APPROVAL
                    : ConversationRoutingDecision.BACKFILL_QUIET;
        }
        if (context.recipientRole() != RecipientRole.AUTHOR) {
            return ConversationRoutingDecision.REVIEWER_DEFERRED;
        }
        if (!practiceResult.getOutcome().isDecided() || practiceResult.getOutcome() != Outcome.NOT_MET) {
            return ConversationRoutingDecision.NOT_DELIVERABLE;
        }
        if (hasNaturalInlineAnchor(practiceResult)) {
            return ConversationRoutingDecision.HAS_INLINE_ANCHOR;
        }
        if (feedbackRepository.existsDeliveredInContextForResult(
                workspaceId, practiceResult.getAboutUserId(), practiceResult.getId())) {
            return ConversationRoutingDecision.ALREADY_DELIVERED_IN_CONTEXT;
        }
        return ConversationRoutingDecision.ADMIT;
    }

    private Map<UUID, PracticeAutonomy> autonomyByPracticeId(
            List<PracticeResult> practiceResults, long workspaceId, PracticeAutonomy workspaceDefault) {
        List<UUID> ids = practiceResults.stream()
                .map(PracticeResult::getId)
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, PracticeAutonomy> autonomyByPracticeId = new HashMap<>();
        for (var row : practiceResultRepository.findPracticeAutonomyFor(ids, workspaceId)) {
            autonomyByPracticeId.put(
                    row.getPracticeResultId(),
                    AutonomyResolver.resolvePractice(
                                    row.getPracticeAutonomy(), row.getGroupAutonomy(), workspaceDefault)
                            .autonomy());
        }
        return autonomyByPracticeId;
    }

    private static boolean hasNaturalInlineAnchor(PracticeResult practiceResult) {
        if (!ArtifactKinds.hasInlineLane(practiceResult.getArtifactKind())) {
            return false;
        }
        JsonNode evidence = practiceResult.getEvidence();
        if (evidence == null) {
            return false;
        }
        JsonNode citations = evidence.path("citations");
        if (!citations.isArray() || citations.isEmpty()) {
            return false;
        }
        for (JsonNode citation : citations) {
            if (PracticePreconditionClause.DIFF_SOURCE
                    .value()
                    .equals(citation.path("sourceKind").asString())) {
                return true;
            }
        }
        return false;
    }
}
