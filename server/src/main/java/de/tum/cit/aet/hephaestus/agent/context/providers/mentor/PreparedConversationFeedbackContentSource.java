package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import de.tum.cit.aet.hephaestus.evidence.SourceUsePurpose;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.feedback.ConversationBriefBody;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.PreparedConversationFact;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationVisibilityPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
        // Load and authorize the bound observations before exposing their evidence to the mentor.
        Map<UUID, Observation> observations = observationsById(workspaceId, prepared);
        Set<UUID> visible = visibilityPolicy.permitsAll(
            workspaceId,
            observations.values(),
            SourceUsePurpose.CONVERSATIONAL_MENTORING
        );

        ObjectNode root = objectMapper.createObjectNode();

        consentGate.writeUntrustedEnvelope(root);

        ArrayNode arr = root.putArray("preparedConversationFeedback");
        for (PreparedConversationFact fact : prepared) {
            if (!visible.contains(fact.getObservationId())) {
                continue;
            }
            if (
                ArtifactKinds.CONVERSATION_THREAD.equals(fact.getArtifactKind()) &&
                (fact.getArtifactId() == null || !activeThreadIds.contains(fact.getArtifactId()))
            ) {
                continue;
            }
            ObjectNode node = arr.addObject();
            node.put("observationId", fact.getObservationId().toString());
            node.put("practiceSlug", fact.getPracticeSlug());
            node.put("practiceName", fact.getPracticeName());
            node.put("summary", fact.getSummary());
            if (fact.getSeverity() != null) {
                node.put("severity", fact.getSeverity().name());
            }
            if (fact.getEvidenceRationale() != null) {
                node.put("evidenceRationale", fact.getEvidenceRationale());
            }
            if (fact.getArtifactKind() != null) {
                node.put("artifactKind", fact.getArtifactKind().value());
            }
            if (fact.getArtifactId() != null) {
                node.put("artifactId", fact.getArtifactId());
            }
            if (fact.getPreparedAt() != null) {
                node.put("preparedAt", fact.getPreparedAt().toString());
            }
            Observation observation = observations.get(fact.getObservationId());
            if (observation != null && observation.getEvidence() != null) {
                // The mentor grounds its own wording in the authorized measurement, not only the brief.
                node.set("evidence", observation.getEvidence());
            }
            writeNotes(node, fact.getBody());
        }
        root.put("totalPrepared", arr.size());
        try {
            files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(root));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize prepared conversation feedback context", e);
        }
    }

    /** Adds structured internal notes when the prepared body contains a complete brief. */
    private static void writeNotes(ObjectNode node, String body) {
        ConversationBriefBody.Brief brief = ConversationBriefBody.parse(body);
        if (brief == null) {
            return;
        }
        node.put("topic", brief.title());
        ObjectNode notes = node.putObject("notes");
        notes.put("situation", brief.situation());
        notes.put("capability", brief.capability());
        notes.put("evidenceSummary", brief.evidenceSummary());
        notes.put("inConversationSignal", brief.inConversationSignal());
    }

    /**
     * The observations behind {@code facts} that this workspace may read, keyed by id. Scoped to the
     * workspace rather than fetched by bare id: the facts are already recipient- and workspace-filtered, so
     * this changes no answer, and it means an observation and the feedback unit citing it can never be read
     * from different tenants.
     */
    private Map<UUID, Observation> observationsById(long workspaceId, List<PreparedConversationFact> facts) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (PreparedConversationFact fact : facts) {
            ids.add(fact.getObservationId());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Observation> rows = observationRepository.findAllByIdInAndWorkspaceId(ids, workspaceId);
        Map<UUID, Observation> byId = new HashMap<>(rows.size());
        for (Observation observation : rows) {
            byId.put(observation.getId(), observation);
        }
        return byId;
    }

    private static List<Long> conversationThreadIds(List<PreparedConversationFact> facts) {
        List<Long> ids = new ArrayList<>();
        for (PreparedConversationFact fact : facts) {
            if (ArtifactKinds.CONVERSATION_THREAD.equals(fact.getArtifactKind()) && fact.getArtifactId() != null) {
                ids.add(fact.getArtifactId());
            }
        }
        return ids;
    }
}
