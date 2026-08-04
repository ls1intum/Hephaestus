package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.PreparedConversationFact;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationVisibilityPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class PreparedConversationFeedbackContentSource implements ContentSource {

    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "prepared_conversation_feedback.json";

    private static final int MAX_PREPARED = 3;

    private final FeedbackObservationRepository feedbackObservationRepository;
    private final ConversationConsentGate consentGate;
    private final ObjectMapper objectMapper;
    private final ObservationRepository observationRepository;
    private final ObservationVisibilityPolicy visibilityPolicy;

    public PreparedConversationFeedbackContentSource(
        FeedbackObservationRepository feedbackObservationRepository,
        ConversationConsentGate consentGate,
        ObjectMapper objectMapper,
        ObservationRepository observationRepository,
        ObservationVisibilityPolicy visibilityPolicy
    ) {
        this.feedbackObservationRepository = feedbackObservationRepository;
        this.consentGate = consentGate;
        this.objectMapper = objectMapper;
        this.observationRepository = observationRepository;
        this.visibilityPolicy = visibilityPolicy;
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
        long workspaceId = req.workspaceId();
        List<PreparedConversationFact> prepared =
            feedbackObservationRepository.findPreparedConversationFactsForRecipient(
                workspaceId,
                req.developerId(),
                PageRequest.of(0, MAX_PREPARED)
            );

        Set<Long> activeThreadIds = consentGate.activeThreadIds(workspaceId, conversationThreadIds(prepared));

        ObjectNode root = objectMapper.createObjectNode();

        consentGate.writeUntrustedEnvelope(root);

        ArrayNode arr = root.putArray("preparedConversationFeedback");
        for (PreparedConversationFact fact : prepared) {
            var observation = observationRepository.findById(fact.getObservationId()).orElse(null);
            if (
                observation == null ||
                !visibilityPolicy.permits(workspaceId, observation, SourceUsePurpose.CONVERSATIONAL_MENTORING)
            ) {
                continue;
            }
            if (
                fact.getArtifactType() == WorkArtifact.CONVERSATION_THREAD &&
                (fact.getArtifactId() == null || !activeThreadIds.contains(fact.getArtifactId()))
            ) {
                continue;
            }
            ObjectNode node = arr.addObject();
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

    private static List<Long> conversationThreadIds(List<PreparedConversationFact> facts) {
        List<Long> ids = new ArrayList<>();
        for (PreparedConversationFact fact : facts) {
            if (fact.getArtifactType() == WorkArtifact.CONVERSATION_THREAD && fact.getArtifactId() != null) {
                ids.add(fact.getArtifactId());
            }
        }
        return ids;
    }
}
