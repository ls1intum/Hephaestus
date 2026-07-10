package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real-Postgres proof of the fail-closed consent gate + untrusted quarantine on {@code delivered_feedback.json}
 * ({@link DeliveredFeedbackContentSource}). A DELIVERED CONVERSATION_THREAD feedback unit whose source Slack channel
 * is no longer ACTIVE (PAUSED / REVOKED) has its body withheld, while an ACTIVE one is surfaced — the same
 * {@code consent_state = 'ACTIVE'} gate the raw {@code SlackConversationProjector} applies. The critical
 * no-regression assertion: PR/ISSUE-derived feedback is ALWAYS surfaced regardless of Slack consent (the gate
 * touches ONLY CONVERSATION_THREAD units), and a PR/issue-only payload carries NO {@code _meta} envelope.
 * Deterministic.
 */
class DeliveredFeedbackConsentGateIntegrationTest extends AbstractSlackConsentGateIntegrationTest {

    @Autowired
    private DeliveredFeedbackContentSource contentSource;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ObjectMapper objectMapper;

    private AgentJob job;
    private int nextPosition;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        var cache = cacheManager.getCache("mentor_delivered_feedback_context");
        if (cache != null) {
            cache.clear();
        }
        setUpWorkspaceAndRecipient("delivered-consent-gate-test");
        job = newJob();
        nextPosition = 0;
    }

    @Test
    @DisplayName("consent gate: only an ACTIVE-channel conversation body surfaces; PAUSED/REVOKED are withheld")
    void onlyActiveChannelConversationBodySurfaces() {
        long activeThreadId = seedThread("C-active", "100.0", ConsentState.ACTIVE);
        long pausedThreadId = seedThread("C-paused", "200.0", ConsentState.PAUSED);
        long revokedThreadId = seedThread("C-revoked", "300.0", ConsentState.REVOKED);

        saveDelivered(WorkArtifact.CONVERSATION_THREAD, activeThreadId, FeedbackChannel.CONVERSATION, "active-body");
        saveDelivered(WorkArtifact.CONVERSATION_THREAD, pausedThreadId, FeedbackChannel.CONVERSATION, "paused-body");
        saveDelivered(WorkArtifact.CONVERSATION_THREAD, revokedThreadId, FeedbackChannel.CONVERSATION, "revoked-body");
        // A PR-derived DELIVERED unit must ALWAYS pass through, regardless of any Slack consent state.
        saveDelivered(WorkArtifact.PULL_REQUEST, 4242L, FeedbackChannel.IN_CONTEXT, "pr-body");

        JsonNode root = contribute();

        // Untrusted-content quarantine envelope is present because a Slack-derived body survived the gate.
        assertThat(root.get("_meta").get("trustLevel").asString()).isEqualTo("UNTRUSTED_EXTERNAL");

        List<String> bodies = bodies(root);
        // ACTIVE conversation body + PR body survive; PAUSED and REVOKED are withheld (fail-closed).
        assertThat(bodies).containsExactlyInAnyOrder("active-body", "pr-body");
    }

    @Test
    @DisplayName("no-regression: PR/ISSUE feedback always surfaces with NO envelope even under zero Slack consent")
    void prIssueOnlyPayloadPassesThroughWithoutEnvelope() {
        saveDelivered(WorkArtifact.PULL_REQUEST, 555L, FeedbackChannel.IN_CONTEXT, "pr-body");
        saveDelivered(WorkArtifact.ISSUE, 777L, FeedbackChannel.IN_CONTEXT, "issue-body");

        JsonNode root = contribute();

        // A PR/issue-only payload keeps its trusted shape: NO untrusted envelope is added.
        assertThat(root.has("_meta")).isFalse();
        assertThat(bodies(root)).containsExactlyInAnyOrder("pr-body", "issue-body");
    }

    @Test
    @DisplayName("no-regression: a PR body surfaces even when the ONLY conversation body is REVOKED")
    void prSurvivesWhenAllConversationRevoked() {
        long revokedThreadId = seedThread("C-revoked", "300.0", ConsentState.REVOKED);
        saveDelivered(WorkArtifact.CONVERSATION_THREAD, revokedThreadId, FeedbackChannel.CONVERSATION, "revoked-body");
        saveDelivered(WorkArtifact.PULL_REQUEST, 909L, FeedbackChannel.IN_CONTEXT, "pr-body");

        JsonNode root = contribute();

        assertThat(root.has("_meta")).isFalse();
        assertThat(bodies(root)).containsExactly("pr-body");
    }

    private JsonNode contribute() {
        Map<String, byte[]> files = new HashMap<>();
        contentSource.contribute(
            new ContextRequest.MentorChatRequest(workspace.getId(), recipient.getId(), UUID.randomUUID()),
            files
        );
        return objectMapper.readTree(files.get(DeliveredFeedbackContentSource.OUTPUT_KEY));
    }

    private static List<String> bodies(JsonNode root) {
        List<String> bodies = new ArrayList<>();
        for (JsonNode node : root.get("deliveredFeedback")) {
            bodies.add(node.get("body").asString());
        }
        return bodies;
    }

    private void saveDelivered(WorkArtifact artifactType, long artifactId, FeedbackChannel channel, String body) {
        Instant now = Instant.now();
        feedbackRepository.save(
            Feedback.builder()
                .agentJobId(job.getId())
                .workspaceId(workspace.getId())
                .artifactType(artifactType)
                .artifactId(artifactId)
                .recipientUserId(recipient.getId())
                .aboutUserId(recipient.getId())
                .channel(channel)
                .position(nextPosition++)
                .deliveryState(FeedbackDeliveryState.DELIVERED)
                .source(FeedbackSource.AGENT)
                .body(body)
                .createdAt(now)
                .deliveredAt(now)
                .build()
        );
    }
}
