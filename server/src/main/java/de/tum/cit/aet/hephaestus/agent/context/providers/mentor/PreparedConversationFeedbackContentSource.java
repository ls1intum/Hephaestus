package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import de.tum.cit.aet.hephaestus.agent.context.ContentSource;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest.MentorChatRequest;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.PreparedConversationFact;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
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

/**
 * Materialises {@code inputs/context/prepared_conversation_feedback.json} for a {@link MentorChatRequest}: the newest
 * PREPARED conversational feedback units queued for the requesting developer - the mentor's "raise these next"
 * shortlist. Facts + practice only, never a body (a PREPARED unit carries a NULL body by construction; the
 * mentor composes the wording at delivery). {@code originId="core"}. Best-effort.
 *
 * <p><strong>Consent gate (fail-closed).</strong> A CONVERSATION_THREAD-derived fact's {@code title}/{@code
 * reasoning} is LLM-composed from the raw messages of a Slack thread's participants, so it may only surface while
 * that thread's source channel consent is still {@code ACTIVE} — the same gate {@code SlackConversationProjector}
 * applies on the raw message read. A paused/revoked/erased channel (or a deleted thread) yields nothing here, so a
 * withdrawn-consent channel's derived reasoning never flows into the developer's next mentor turn. Facts derived
 * from a PR or issue carry no Slack content and are surfaced unconditionally. The check is raw JDBC by table name
 * (no Hibernate, explicit {@code workspace_id} pin) mirroring the projector, so no cross-module import is added.
 *
 * <p><strong>Untrusted-content quarantine.</strong> Because a fact's title/reasoning is model output over
 * attacker-controlled third-party text, the whole payload carries the {@code _meta.trustLevel:
 * "UNTRUSTED_EXTERNAL"} envelope (matching the projector) and the mentor {@code system.md} names this file in its
 * untrusted-content list, so a surviving injection is treated as DATA, never as instructions.
 */
@Component
public class PreparedConversationFeedbackContentSource implements ContentSource {

    /** Workspace-relative output key. Whitelisted in {@code MentorContextKeys#ALLOWED_OUTPUT_KEYS}. */
    public static final String OUTPUT_KEY = OUTPUT_PREFIX + "prepared_conversation_feedback.json";

    private static final int MAX_PREPARED = 3;

    private final FeedbackObservationRepository feedbackObservationRepository;
    private final ConversationConsentGate consentGate;
    private final ObjectMapper objectMapper;

    public PreparedConversationFeedbackContentSource(
        FeedbackObservationRepository feedbackObservationRepository,
        ConversationConsentGate consentGate,
        ObjectMapper objectMapper
    ) {
        this.feedbackObservationRepository = feedbackObservationRepository;
        this.consentGate = consentGate;
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
        long workspaceId = req.workspaceId();
        List<PreparedConversationFact> prepared =
            feedbackObservationRepository.findPreparedConversationFactsForRecipient(
                workspaceId,
                req.developerId(),
                PageRequest.of(0, MAX_PREPARED)
            );

        // Fail-closed consent gate for the Slack-derived facts (see class javadoc): only CONVERSATION_THREAD facts
        // whose source channel is still ACTIVE survive; a non-ACTIVE (paused/revoked/erased) channel or a deleted
        // thread contributes no id and its fact is dropped.
        Set<Long> activeThreadIds = consentGate.activeThreadIds(workspaceId, conversationThreadIds(prepared));

        ObjectNode root = objectMapper.createObjectNode();

        // Prompt-injection quarantine: these titles/reasonings are model output over untrusted, attacker-controlled
        // third-party content. This file is dedicated to conversational feedback, so it ALWAYS carries the untrusted
        // envelope (matching SlackConversationProjector) — a surviving injection is never obeyed.
        consentGate.writeUntrustedEnvelope(root);

        ArrayNode arr = root.putArray("preparedConversationFeedback");
        for (PreparedConversationFact fact : prepared) {
            if (
                fact.getArtifactType() == WorkArtifact.CONVERSATION_THREAD &&
                (fact.getArtifactId() == null || !activeThreadIds.contains(fact.getArtifactId()))
            ) {
                // Source Slack channel no longer ACTIVE, or its thread is gone — withheld (fail-closed).
                continue;
            }
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

    /** The {@code slack_thread} ids ({@code artifactId}) of the CONVERSATION_THREAD-derived facts, if any. */
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
