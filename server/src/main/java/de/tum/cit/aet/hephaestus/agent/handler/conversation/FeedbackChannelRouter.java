package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Decides which of a cycle's observations are eligible for conversational delivery (S7). An observation is
 * {@link ConversationRoutingDecision#ADMIT admitted} to the CONVERSATION channel iff ALL of: author-targeted, a
 * {@link Assessment#BAD} problem, has no natural inline anchor, and does not share a {@code recurrence_key} with a
 * DELIVERED IN_CONTEXT unit for the same recipient. Every other case is a named, testable non-admission reason.
 *
 * <p>Pure routing - it reads the feedback ledger but writes nothing. The {@link ConversationalFeedbackPreparer}
 * turns the admitted set into PREPARED units.
 */
@Component
public class FeedbackChannelRouter {

    private final FeedbackRepository feedbackRepository;

    public FeedbackChannelRouter(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    /** The observations from {@code observations} that are eligible for conversational delivery, order preserved. */
    public List<Observation> admit(List<Observation> observations, long workspaceId, RoutingContext context) {
        List<Observation> admitted = new ArrayList<>();
        for (Observation observation : observations) {
            if (route(observation, workspaceId, context) == ConversationRoutingDecision.ADMIT) {
                admitted.add(observation);
            }
        }
        return admitted;
    }

    /** Route a single observation. See the class javadoc for the admission predicate. */
    public ConversationRoutingDecision route(Observation observation, long workspaceId, RoutingContext context) {
        // Reviewer-targeted delivery: explicit deferral behind ADR-0021-C2 attribution (not a silent omission).
        if (context.recipientRole() != RecipientRole.AUTHOR) {
            return ConversationRoutingDecision.REVIEWER_DEFERRED;
        }
        // Only a problem is raised in a coaching turn; strengths and NOT_APPLICABLE abstentions are not delivered.
        if (observation.getPresence() == Presence.NOT_APPLICABLE || observation.getAssessment() != Assessment.BAD) {
            return ConversationRoutingDecision.NOT_DELIVERABLE;
        }
        // A locus with a natural diff anchor belongs in-context, not in the conversation.
        if (hasNaturalInlineAnchor(observation)) {
            return ConversationRoutingDecision.HAS_INLINE_ANCHOR;
        }
        // Do not re-raise a locus the developer already received in-context (keyed on the cross-run recurrence key).
        String recurrenceKey = observation.getRecurrenceKey();
        if (
            recurrenceKey != null &&
            feedbackRepository.existsDeliveredInContextForRecurrenceKey(
                workspaceId,
                observation.getAboutUserId(),
                recurrenceKey
            )
        ) {
            return ConversationRoutingDecision.ALREADY_DELIVERED_IN_CONTEXT;
        }
        return ConversationRoutingDecision.ADMIT;
    }

    /**
     * A natural inline anchor exists when the observation can be posted as a diff note: a PR observation whose
     * evidence carries at least one location with a non-blank file path. Issue observations (no diff) and PR
     * observations with no file location have no natural inline home and are candidates for the conversation.
     */
    private static boolean hasNaturalInlineAnchor(Observation observation) {
        if (observation.getArtifactType() != WorkArtifact.PULL_REQUEST) {
            return false;
        }
        JsonNode evidence = observation.getEvidence();
        if (evidence == null) {
            return false;
        }
        JsonNode locations = evidence.path("locations");
        if (!locations.isArray() || locations.isEmpty()) {
            return false;
        }
        JsonNode path = locations.get(0).path("path");
        return path.isTextual() && !path.asString().isBlank();
    }
}
