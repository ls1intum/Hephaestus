package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationalFeedbackPreparer;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.FeedbackChannelRouter;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.RoutingContext;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Real-Postgres proof of the fail-closed consent gate + untrusted quarantine on the derived-feedback content
 * source. A PREPARED CONVERSATION fact whose source Slack channel is no longer ACTIVE (PAUSED / REVOKED) is NOT
 * surfaced by {@link PreparedConversationFeedbackContentSource}, while an ACTIVE one is — the same
 * {@code consent_state = 'ACTIVE'} gate the raw {@code SlackConversationProjector} applies. A CONVERSATION unit
 * derived from a non-Slack artifact (a PR) is surfaced unconditionally: the gate applies only to the
 * Slack-content-bearing CONVERSATION_THREAD facts. Deterministic.
 */
class PreparedConversationFeedbackConsentGateIntegrationTest extends AbstractSlackConsentGateIntegrationTest {

    @Autowired
    private PreparedConversationFeedbackContentSource contentSource;

    @Autowired
    private FeedbackChannelRouter router;

    @Autowired
    private ConversationalFeedbackPreparer preparer;

    @Autowired
    private FeedbackObservationRepository feedbackObservationRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Practice practice;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        setUpWorkspaceAndRecipient("conv-consent-gate-test");
        practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug("test-practice");
        practice.setName("Test Practice");
        practice.setCriteria("Test description");
        practice.setTriggerEvents(OM.valueToTree(List.of("PullRequestCreated")));
        practice = practiceRepository.save(practice);
    }

    @Test
    @DisplayName("consent gate: only an ACTIVE-channel thread's derived fact surfaces; PAUSED/REVOKED are withheld")
    void onlyActiveChannelDerivedFactSurfaces() {
        long activeThreadId = seedThread("C-active", "100.0", ConsentState.ACTIVE);
        long pausedThreadId = seedThread("C-paused", "200.0", ConsentState.PAUSED);
        long revokedThreadId = seedThread("C-revoked", "300.0", ConsentState.REVOKED);

        AgentJob job = newJob();
        Observation activeObs = saveConversationObservation(job, "occ-active", activeThreadId);
        saveConversationObservation(job, "occ-paused", pausedThreadId);
        saveConversationObservation(job, "occ-revoked", revokedThreadId);
        prepareFor(job);

        // Repository returns all three PREPARED facts — the gate is enforced by the content source, not the query.
        assertThat(
            feedbackObservationRepository.findPreparedConversationFactsForRecipient(
                workspace.getId(),
                recipient.getId(),
                PageRequest.of(0, 10)
            )
        ).hasSize(3);

        JsonNode root = contribute();

        // Untrusted-content quarantine envelope (matches SlackConversationProjector).
        assertThat(root.get("_meta").get("trustLevel").asString()).isEqualTo("UNTRUSTED_EXTERNAL");

        // Only the ACTIVE-channel fact survives the fail-closed gate.
        JsonNode arr = root.get("preparedConversationFeedback");
        assertThat(arr).hasSize(1);
        assertThat(arr.get(0).get("findingId").asString()).isEqualTo(activeObs.getId().toString());
        assertThat(arr.get(0).get("artifactType").asString()).isEqualTo("CONVERSATION_THREAD");
        assertThat(arr.get(0).get("artifactId").asLong()).isEqualTo(activeThreadId);
        assertThat(root.get("totalPrepared").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("gate scope: a CONVERSATION unit derived from a non-Slack (PR) artifact is surfaced unconditionally")
    void nonSlackArtifactFactAlwaysSurfaces() {
        AgentJob job = newJob();
        // A PR-derived observation with no inline anchor is routed to the CONVERSATION channel too; it carries no
        // Slack content, so the consent gate must NOT filter it (there is no monitored channel at all here).
        Observation prObs = savePullRequestObservation(job, "occ-pr", 4242L);
        prepareFor(job);

        JsonNode root = contribute();
        JsonNode arr = root.get("preparedConversationFeedback");
        assertThat(arr).hasSize(1);
        assertThat(arr.get(0).get("findingId").asString()).isEqualTo(prObs.getId().toString());
        assertThat(arr.get(0).get("artifactType").asString()).isEqualTo("PULL_REQUEST");
    }

    private JsonNode contribute() {
        Map<String, byte[]> files = new HashMap<>();
        contentSource.contribute(
            new ContextRequest.MentorChatRequest(workspace.getId(), recipient.getId(), UUID.randomUUID()),
            files
        );
        return objectMapper.readTree(files.get(PreparedConversationFeedbackContentSource.OUTPUT_KEY));
    }

    private void prepareFor(AgentJob job) {
        List<Observation> observations = observationRepository.findByAgentJobId(job.getId());
        List<Observation> admitted = router.admit(observations, workspace.getId(), RoutingContext.author());
        preparer.prepare(job.getId(), workspace.getId(), admitted);
    }

    private Observation saveConversationObservation(AgentJob job, String occurrenceKey, long threadId) {
        return saveObservation(job, occurrenceKey, "CONVERSATION_THREAD", threadId);
    }

    private Observation savePullRequestObservation(AgentJob job, String occurrenceKey, long pullRequestId) {
        return saveObservation(job, occurrenceKey, "PULL_REQUEST", pullRequestId);
    }

    private Observation saveObservation(AgentJob job, String occurrenceKey, String artifactType, long artifactId) {
        UUID id = UUID.randomUUID();
        observationRepository.insertIfAbsent(
            id,
            occurrenceKey,
            job.getId(),
            practice.getId(),
            null,
            artifactType,
            artifactId,
            recipient.getId(),
            "Observation title",
            "ABSENT",
            "BAD",
            "MAJOR",
            0.8f,
            null,
            null,
            null,
            Instant.now()
        );
        return observationRepository.findById(id).orElseThrow();
    }
}
