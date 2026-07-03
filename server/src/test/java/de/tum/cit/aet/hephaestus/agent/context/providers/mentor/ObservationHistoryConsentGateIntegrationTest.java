package de.tum.cit.aet.hephaestus.agent.context.providers.mentor;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.context.ContextRequest;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannel.ConsentState;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackMonitoredChannelRepository;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThread;
import de.tum.cit.aet.hephaestus.integration.slack.domain.SlackThreadRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
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
 * Real-Postgres proof of the fail-closed consent gate + untrusted quarantine on {@code findings_history.json}
 * ({@link ObservationHistoryContentSource}). A CONVERSATION_THREAD observation whose source Slack channel is no
 * longer ACTIVE (PAUSED / REVOKED) is NOT surfaced, while an ACTIVE one is — the same {@code consent_state = 'ACTIVE'}
 * gate the raw {@code SlackConversationProjector} applies. The critical no-regression assertion: a PR/ISSUE-derived
 * observation is ALWAYS present regardless of Slack consent (the gate touches ONLY CONVERSATION_THREAD rows), and a
 * PR/issue-only payload carries NO {@code _meta} envelope (its trusted shape is untouched). Deterministic.
 */
class ObservationHistoryConsentGateIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OM = new ObjectMapper();

    @Autowired
    private ObservationHistoryContentSource contentSource;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    @Autowired
    private SlackThreadRepository slackThreadRepository;

    @Autowired
    private SlackMonitoredChannelRepository slackMonitoredChannelRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private ObjectMapper objectMapper;

    private Workspace workspace;
    private Practice practice;
    private User recipient;
    private AgentJob job;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        // cleanDatabase truncates rows but not the in-memory findings cache; clear it so a prior method's payload
        // (same workspaceId:developerId key after an identity reset) cannot leak into this one.
        var cache = cacheManager.getCache("mentor_findings_context");
        if (cache != null) {
            cache.clear();
        }
        workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("obs-consent-gate-test"));
        practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug("test-practice");
        practice.setName("Test Practice");
        practice.setCriteria("Test description");
        practice.setTriggerEvents(OM.valueToTree(List.of("PullRequestCreated")));
        practice = practiceRepository.save(practice);
        IdentityProvider provider = identityProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseGet(() ->
                identityProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
            );
        recipient = userRepository.save(TestUserFactory.createUser(100L, "recipient", provider));
        job = newJob();
    }

    @Test
    @DisplayName("consent gate: only an ACTIVE-channel conversation observation surfaces; PAUSED/REVOKED are withheld")
    void onlyActiveChannelConversationObservationSurfaces() {
        long activeThreadId = seedThread("C-active", "100.0", ConsentState.ACTIVE);
        long pausedThreadId = seedThread("C-paused", "200.0", ConsentState.PAUSED);
        long revokedThreadId = seedThread("C-revoked", "300.0", ConsentState.REVOKED);

        Observation activeObs = saveObservation("occ-active", "CONVERSATION_THREAD", activeThreadId);
        saveObservation("occ-paused", "CONVERSATION_THREAD", pausedThreadId);
        saveObservation("occ-revoked", "CONVERSATION_THREAD", revokedThreadId);
        // A PR-derived observation must ALWAYS pass through, regardless of any Slack consent state.
        Observation prObs = saveObservation("occ-pr", "PULL_REQUEST", 4242L);

        JsonNode root = contribute();

        // Untrusted-content quarantine envelope is present because a Slack-derived observation survived the gate.
        assertThat(root.get("_meta").get("trustLevel").asString()).isEqualTo("UNTRUSTED_EXTERNAL");

        List<String> ids = observationIds(root);
        // ACTIVE conversation + PR survive; PAUSED and REVOKED conversation observations are withheld (fail-closed).
        assertThat(ids).containsExactlyInAnyOrder(activeObs.getId().toString(), prObs.getId().toString());
        assertThat(ids).doesNotContainNull();
        assertThat(ids).hasSize(2);
    }

    @Test
    @DisplayName("no-regression: PR/ISSUE observations always surface with NO envelope even under zero Slack consent")
    void prIssueOnlyPayloadPassesThroughWithoutEnvelope() {
        // No monitored channels at all. Two non-Slack observations that must be surfaced unconditionally.
        Observation prObs = saveObservation("occ-pr", "PULL_REQUEST", 555L);
        Observation issueObs = saveObservation("occ-issue", "ISSUE", 777L);

        JsonNode root = contribute();

        // A PR/issue-only payload keeps its trusted shape: NO untrusted envelope is added.
        assertThat(root.has("_meta")).isFalse();
        assertThat(observationIds(root)).containsExactlyInAnyOrder(
            prObs.getId().toString(),
            issueObs.getId().toString()
        );
    }

    @Test
    @DisplayName("no-regression: a PR observation surfaces even when the ONLY conversation observation is REVOKED")
    void prSurvivesWhenAllConversationRevoked() {
        long revokedThreadId = seedThread("C-revoked", "300.0", ConsentState.REVOKED);
        saveObservation("occ-revoked", "CONVERSATION_THREAD", revokedThreadId);
        Observation prObs = saveObservation("occ-pr", "PULL_REQUEST", 909L);

        JsonNode root = contribute();

        // The revoked conversation row is dropped, so no survivor → no envelope; the PR row is untouched.
        assertThat(root.has("_meta")).isFalse();
        assertThat(observationIds(root)).containsExactly(prObs.getId().toString());
    }

    private JsonNode contribute() {
        Map<String, byte[]> files = new HashMap<>();
        contentSource.contribute(
            new ContextRequest.MentorChatRequest(workspace.getId(), recipient.getId(), UUID.randomUUID()),
            files
        );
        return objectMapper.readTree(files.get(ObservationHistoryContentSource.OUTPUT_KEY));
    }

    private static List<String> observationIds(JsonNode root) {
        List<String> ids = new ArrayList<>();
        for (JsonNode node : root.get("recentObservations")) {
            ids.add(node.get("id").asString());
        }
        return ids;
    }

    /** Seed a monitored channel at {@code consent} plus one thread on it; return the generated {@code slack_thread.id}. */
    private long seedThread(String channelId, String threadTs, ConsentState consent) {
        SlackMonitoredChannel channel = new SlackMonitoredChannel();
        channel.setWorkspaceId(workspace.getId());
        channel.setSlackTeamId("T1");
        channel.setSlackChannelId(channelId);
        channel.setConsentState(consent);
        slackMonitoredChannelRepository.save(channel);

        SlackThread thread = new SlackThread();
        thread.setWorkspaceId(workspace.getId());
        thread.setSlackChannelId(channelId);
        thread.setSlackThreadTs(threadTs);
        return slackThreadRepository.save(thread).getId();
    }

    private AgentJob newJob() {
        AgentJob j = new AgentJob();
        j.setWorkspace(workspace);
        j.setJobType(AgentJobType.CONVERSATION_REVIEW);
        j.setConfigSnapshot(OM.valueToTree(Map.of("model", "test")));
        return agentJobRepository.save(j);
    }

    private Observation saveObservation(String occurrenceKey, String artifactType, long artifactId) {
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
