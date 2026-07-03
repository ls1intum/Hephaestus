package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.PreparedConversationFact;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Materialises {@code inputs/context/prepared_conversation_feedback.json} for a {@link MentorChatRequest}: the newest
 * PREPARED conversational feedback units queued for the requesting developer - the mentor's "raise these next"
 * shortlist (S7). Facts + practice only, never a body (a PREPARED unit carries a NULL body by construction; the
 * mentor composes the wording at delivery). {@code originId="core"}. Best-effort.
 */
@Component
public class PreparedConversationFeedbackContentSource implements ContentSource {

    /** Workspace-relative output key. Whitelisted in {@code MentorContextKeys#ALLOWED_OUTPUT_KEYS}. */
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "prepared_conversation_feedback.json";

    private static final int MAX_PREPARED = 3;

    private final FeedbackObservationRepository feedbackObservationRepository;
    private final ObjectMapper objectMapper;

    public PreparedConversationFeedbackContentSource(
        FeedbackObservationRepository feedbackObservationRepository,
        ObjectMapper objectMapper
    ) {
        this.feedbackObservationRepository = feedbackObservationRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String originId() {
        return "core";
    }

    @Override
    public boolean supports(ContextRequest request) {
        return request instanceof MentorChatRequest;
    }

    @Override
    public boolean required() {
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public void contribute(ContextRequest request, Map<String, byte[]> files) {
        MentorChatRequest req = (MentorChatRequest) request;
        List<PreparedConversationFact> prepared =
            feedbackObservationRepository.findPreparedConversationFactsForRecipient(
                req.workspaceId(),
                req.developerId(),
                PageRequest.of(0, MAX_PREPARED)
            );

        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode arr = root.putArray("preparedConversationFeedback");
        for (PreparedConversationFact fact : prepared) {
            ObjectNode node = arr.addObject();
            // Surface the OBSERVATION id (the link_finding handle), not the feedback id: the mentor raises a locus by
            // linking the observation, and the reconciler maps that observation back to its PREPARED unit.
            node.put("findingId", fact.getObservationId().toString());
            node.put("practiceSlug", fact.getPracticeSlug());
            node.put("practiceName", fact.getPracticeName());
            node.put("title", fact.getTitle());
            if (fact.getSeverity() != null) {
                node.put("severity", fact.getSeverity().name());
            }
            if (fact.getReasoning() != null) {
                node.put("reasoning", fact.getReasoning());
            }
            if (fact.getArtifactType() != null) {
                node.put("artifactType", fact.getArtifactType().name());
            }
            if (fact.getArtifactId() != null) {
                node.put("artifactId", fact.getArtifactId());
            }
            if (fact.getPreparedAt() != null) {
                node.put("preparedAt", fact.getPreparedAt().toString());
            }
        }
        root.put("totalPrepared", arr.size());
        try {
            files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(root));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize prepared conversation feedback context", e);
        }
    }
}
