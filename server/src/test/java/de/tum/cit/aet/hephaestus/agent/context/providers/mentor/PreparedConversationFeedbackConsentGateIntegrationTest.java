package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.handler.composition.ComposedFeedbackUnit;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationalFeedbackPreparer;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.FeedbackChannelRouter;
import de.tum.cit.aet.hephaestus.agent.handler.conversation.RoutingContext;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.core.EntityTagPrecondition;
import de.tum.cit.aet.hephaestus.core.settings.InstanceSettingsService;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
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
    private PracticeRevisionRepository practiceRevisionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InstanceSettingsService instanceSettingsService;

    private Practice practice;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        var settings = instanceSettingsService.get();
        instanceSettingsService.updateSilentMode(
            false,
            null,
            null,
            EntityTagPrecondition.parse("\"" + settings.getVersion() + "\"")
        );
        setUpWorkspaceAndRecipient("conv-consent-gate-test");
        practice = new Practice();
        practice.setBindings(PracticeTestEvidence.bindings(ArtifactKinds.CONVERSATION_THREAD));
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.conversationThread());
        practice.setWorkspace(workspace);
        practice.setSlug("test-practice");
        practice.setName("Test Practice");
        practice.setCriteria("Test description");
        practice.setBindings(PracticeTestEvidence.bindings(ScmSignals.PULL_REQUEST_OPENED));
        practice = practiceRepository.saveAndFlush(practice);
        PracticeRevision revision = practiceRevisionRepository.save(new PracticeRevision(practice, 1));
        practice.setCurrentRevision(revision);
        practice = practiceRepository.saveAndFlush(practice);
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

        assertThat(
            feedbackObservationRepository.findPreparedConversationFactsForRecipient(
                workspace.getId(),
                recipient.getId(),
                PageRequest.of(0, 10)
            )
        ).hasSize(3);

        JsonNode root = contribute();

        assertThat(root.get("_meta").get("trustLevel").asString()).isEqualTo("UNTRUSTED_EXTERNAL");

        JsonNode arr = root.get("preparedConversationFeedback");
        assertThat(arr).hasSize(1);
        assertThat(arr.get(0).get("observationId").asString()).isEqualTo(activeObs.getId().toString());
        assertThat(arr.get(0).get("artifactKind").asString()).isEqualTo("chat.conversation_thread");
        assertThat(arr.get(0).get("artifactId").asLong()).isEqualTo(activeThreadId);
        assertThat(root.get("totalPrepared").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("Slack consent does not suppress an otherwise-authorized pull-request observation")
    void nonSlackArtifactFactAlwaysSurfaces() {
        practice.setBindings(PracticeTestEvidence.bindings(ArtifactKinds.PULL_REQUEST));
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        PracticeRevision revision = practiceRevisionRepository.save(new PracticeRevision(practice, 2));
        practice.setCurrentRevision(revision);
        practice = practiceRepository.saveAndFlush(practice);

        AgentJob job = newJob();
        Observation prObs = savePullRequestObservation(job, "occ-pr", 4242L);
        prepareFor(job);

        JsonNode root = contribute();
        JsonNode arr = root.get("preparedConversationFeedback");
        assertThat(arr).hasSize(1);
        assertThat(arr.get(0).get("observationId").asString()).isEqualTo(prObs.getId().toString());
        assertThat(arr.get(0).get("artifactKind").asString()).isEqualTo("scm.pull_request");
    }

    /**
     * The composer's notes have to reach the mentor's sandbox in all four parts, and they have to arrive as
     * {@code notes} rather than as anything a prompt could read as text to paste - the mentor reads the key
     * before it reads the values. The fallback is a missing key, not an empty object, so "nothing was
     * composed" cannot read as "the composer had nothing to say".
     */
    @Test
    @DisplayName("composed notes reach the mentor as notes, and an uncomposed unit carries none")
    void stagesTheComposedNotes() {
        practice.setBindings(PracticeTestEvidence.bindings(ArtifactKinds.PULL_REQUEST));
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        PracticeRevision revision = practiceRevisionRepository.save(new PracticeRevision(practice, 2));
        practice.setCurrentRevision(revision);
        practice = practiceRepository.saveAndFlush(practice);

        AgentJob job = newJob();
        savePullRequestObservation(job, "occ-composed", 4242L);
        List<Observation> admitted = router.admit(
            observationRepository.findByAgentJobId(job.getId()),
            workspace.getId(),
            RoutingContext.author()
        );
        preparer.prepare(
            job.getId(),
            workspace.getId(),
            admitted,
            List.of(
                new ComposedFeedbackUnit(
                    FeedbackChannel.IN_CHAT,
                    practice.getSlug(),
                    List.of("obs-0"),
                    ComposedFeedbackUnit.Action.NEW,
                    null,
                    null,
                    "The test arrives after the review",
                    null,
                    null,
                    new ComposedFeedbackUnit.ConversationBrief(
                        "On !18, !20 and !22 the test landed a push after the review comment.",
                        "Writing the test last is what leaves the review to find the gap.",
                        "On !18, !20 and !22 the test arrived a push later.",
                        "They name a check they could run before pushing."
                    ),
                    null
                )
            )
        );

        JsonNode item = contribute().get("preparedConversationFeedback").get(0);
        assertThat(item.has("body")).isFalse();
        assertThat(item.get("topic").asString()).isEqualTo("The test arrives after the review");
        assertThat(item.get("evidence").get("citations").get(0).get("quote").asString()).isEqualTo("example");
        JsonNode notes = item.get("notes");
        assertThat(notes.get("situation").asString()).isEqualTo(
            "On !18, !20 and !22 the test landed a push after the review comment."
        );
        assertThat(notes.get("capability").asString()).isEqualTo(
            "Writing the test last is what leaves the review to find the gap."
        );
        assertThat(notes.get("evidenceSummary").asString()).isEqualTo(
            "On !18, !20 and !22 the test arrived a push later."
        );
        assertThat(notes.get("inConversationSignal").asString()).isEqualTo(
            "They name a check they could run before pushing."
        );

        // The same surface, prepared with nothing composed: no notes key at all, on that item alone.
        AgentJob uncomposed = newJob();
        savePullRequestObservation(uncomposed, "occ-uncomposed", 4343L);
        prepareFor(uncomposed);

        JsonNode both = contribute().get("preparedConversationFeedback");
        assertThat(both).hasSize(2);
        assertThat(both)
            .filteredOn(node -> node.has("notes"))
            .hasSize(1);
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
        preparer.prepare(job.getId(), workspace.getId(), admitted, List.of());
    }

    private Observation saveConversationObservation(AgentJob job, String occurrenceKey, long threadId) {
        return saveObservation(job, occurrenceKey, "chat.conversation_thread", threadId);
    }

    private Observation savePullRequestObservation(AgentJob job, String occurrenceKey, long pullRequestId) {
        return saveObservation(job, occurrenceKey, "scm.pull_request", pullRequestId);
    }

    private Observation saveObservation(AgentJob job, String occurrenceKey, String artifactKind, long artifactId) {
        UUID id = UUID.randomUUID();
        observationRepository.insertIfAbsent(
            id,
            occurrenceKey,
            job.getId(),
            practice.getId(),
            practice.getCurrentRevision().getId(),
            artifactKind,
            artifactId,
            recipient.getId(),
            "Observation title",
            "ABSENT",
            "BAD",
            "MAJOR",
            artifactKind.equals("chat.conversation_thread")
                ? "{\"citations\":[{\"sourceKind\":\"slack.conversation.thread\",\"artifactPath\":\"inputs/context/thread.json\",\"path\":\"Slack thread\",\"startLine\":1,\"endLine\":1,\"quote\":\"example\",\"quoteRedacted\":false}]}"
                : "{\"citations\":[{\"sourceKind\":\"scm.pull-request.core\",\"artifactPath\":\"inputs/context/pull-request.json\",\"path\":\"pull-request.json\",\"startLine\":1,\"endLine\":1,\"quote\":\"example\",\"quoteRedacted\":false}]}",
            null,
            null,
            Instant.now(),
            "LIVE"
        );
        return observationRepository.findById(id).orElseThrow();
    }
}
