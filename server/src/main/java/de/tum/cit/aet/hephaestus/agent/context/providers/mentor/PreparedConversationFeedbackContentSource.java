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
        // The facts carry no evidence, so each one has to be read back and authorized before it may be
        // quoted at the mentor. Both reads are batched: the per-fact form spent a fetch plus an
        // authorization round trip on every row, and the fetch was itself lazy about the practice revision
        // the currentness test compares.
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
            // Membership is the whole answer, and it covers both refusals the per-fact form made: an
            // observation this workspace could not read never entered the batch, so it can never be in the
            // permitted set either.
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
            if (fact.getArtifactKind() != null) {
                node.put("artifactKind", fact.getArtifactKind().value());
            }
            if (fact.getArtifactId() != null) {
                node.put("artifactId", fact.getArtifactId());
            }
            if (fact.getPreparedAt() != null) {
                node.put("preparedAt", fact.getPreparedAt().toString());
            }
            writeMove(node, fact.getBody());
        }
        root.put("totalPrepared", arr.size());
        try {
            files.put(OUTPUT_KEY, objectMapper.writeValueAsBytes(root));
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize prepared conversation feedback context", e);
        }
    }

    /**
     * The composer's move for one prepared unit, when there is one.
     *
     * <p>Written under {@code move}, and never under {@code body} or {@code message}, because it is not text
     * to speak: {@code opener} is the question to ask before anything is told, {@code evidence} is what to
     * show only once the developer has answered, and {@code target} is what the turn is trying to leave them
     * able to do for themselves. The mentor still writes the words of the turn.
     *
     * <p>A unit prepared without a move simply has none, and the mentor composes from the fact as it always
     * has - the fallback is a missing key, not an empty object, so "nothing was composed" cannot read as "the
     * composer had nothing to say".
     */
    private static void writeMove(ObjectNode node, String body) {
        ConversationBriefBody.Brief brief = ConversationBriefBody.parse(body);
        if (brief == null) {
            return;
        }
        ObjectNode move = node.putObject("move");
        move.put("opener", brief.opener());
        move.put("evidence", brief.evidence());
        move.put("target", brief.target());
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
