package de.tum.cit.aet.hephaestus.agent.handler.conversation;

import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/**
 * Decides which of a cycle's observations are eligible for conversational delivery. An observation is
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
        // Reviewer-targeted delivery: deferred (ADR-0021-C2).
        if (context.recipientRole() != RecipientRole.AUTHOR) {
            return ConversationRoutingDecision.REVIEWER_DEFERRED;
        }
        // Only a problem is raised in a coaching turn; strengths, abstentions and undecided measurements
        // are not delivered.
        if (!observation.getPresence().carriesValence() || observation.getAssessment() != Assessment.BAD) {
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

    private static boolean hasNaturalInlineAnchor(Observation observation) {
        if (!ArtifactKinds.hasInlineLane(observation.getArtifactKind())) {
            return false;
        }
        JsonNode evidence = observation.getEvidence();
        if (evidence == null) {
            return false;
        }
        JsonNode citations = evidence.path("citations");
        if (!citations.isArray() || citations.isEmpty()) {
            return false;
        }
        for (JsonNode citation : citations) {
            if ("scm.pull-request.diff".equals(citation.path("sourceKind").asString())) {
                return true;
            }
        }
        return false;
    }
}
