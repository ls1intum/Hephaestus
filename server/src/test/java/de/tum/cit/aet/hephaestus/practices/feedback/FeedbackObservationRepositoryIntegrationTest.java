package de.tum.cit.aet.hephaestus.practices.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackObservationRepository.ObservationAdviceBody;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

/**
 * Real-Postgres proof for the {@link FeedbackObservation} join — the M:N binding {@link Feedback} to the
 * {@link Observation}s it was composed from. {@code FeedbackLedgerRecorderTest} mocks this repository, so
 * the {@code @EmbeddedId}/{@code @MapsId} key round-trip, the native {@code ON CONFLICT DO NOTHING} upsert,
 * the JPQL read that navigates {@code ff.feedback.body}/{@code deliveryState} through a LAZY association, and
 * the {@code @OnDelete} cascade are only verified end-to-end here.
 */
class FeedbackObservationRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private FeedbackObservationRepository feedbackObservationRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

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
    private IdentityProviderRepository gitProviderRepository;

    private Workspace workspace;
    private Practice practice;
    private AgentJob agentJob;
    private User recipient;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("feedback-observation-test"));

        practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug("test-practice");
        practice.setName("Test Practice");
        practice.setCriteria("Test description");
        practice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        practice = practiceRepository.save(practice);

        agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob = agentJobRepository.save(agentJob);

        IdentityProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseGet(() ->
                gitProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
            );
        recipient = userRepository.save(TestUserFactory.createUser(100L, "recipient", provider));
    }

    @Test
    @DisplayName(
        "insertIfAbsent is idempotent: a second insert on the same (feedback, observation) returns 0 and keeps the original role/ordinal"
    )
    void insertIfAbsentIsIdempotent() {
        Feedback feedback = saveFeedback(0, FeedbackDeliveryState.DELIVERED, "Delivered advice body");
        Observation observation = saveObservation("obs-1");

        int first = feedbackObservationRepository.insertIfAbsent(feedback.getId(), observation.getId(), "PRIMARY", 0);
        // Same key, different role/ordinal — the ON CONFLICT DO NOTHING upsert must NOT overwrite.
        int second = feedbackObservationRepository.insertIfAbsent(
            feedback.getId(),
            observation.getId(),
            "SUPPORTING",
            7
        );

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(0);

        assertThat(feedbackObservationRepository.findAll()).hasSize(1);
        FeedbackObservation row = feedbackObservationRepository.findAll().get(0);
        assertThat(row.getRole()).isEqualTo(EvidenceRole.PRIMARY);
        assertThat(row.getOrdinal()).isEqualTo(0);
        assertThat(row.getFeedback().getId()).isEqualTo(feedback.getId());
        assertThat(row.getObservation().getId()).isEqualTo(observation.getId());
    }

    @Test
    @DisplayName(
        "findAdviceBodiesByObservationIds returns DELIVERED and FAILED bodies (the dashboard's own channel) and " +
            "excludes PREPARED/SUPPRESSED/null-body units"
    )
    void findAdviceBodiesIncludesFailedExcludesPreparedSuppressed() {
        Observation delivered = saveObservation("obs-delivered");
        Observation failed = saveObservation("obs-failed");
        Observation prepared = saveObservation("obs-prepared");
        Observation suppressed = saveObservation("obs-suppressed");
        Observation nullBody = saveObservation("obs-null-body");

        bind(saveFeedback(0, FeedbackDeliveryState.DELIVERED, "The advice the student saw"), delivered);
        // A composed body that never reached the SCM surface still belongs on the dashboard (its own channel).
        bind(saveFeedback(4000, FeedbackDeliveryState.FAILED, "The advice the direct post could not place"), failed);
        bind(saveFeedback(1, FeedbackDeliveryState.PREPARED, "Not yet delivered"), prepared);
        bind(saveFeedback(2, FeedbackDeliveryState.SUPPRESSED, "Withheld"), suppressed);
        bind(saveFeedback(3, FeedbackDeliveryState.DELIVERED, null), nullBody);

        List<ObservationAdviceBody> bodies = feedbackObservationRepository.findAdviceBodiesByObservationIds(
            List.of(delivered.getId(), failed.getId(), prepared.getId(), suppressed.getId(), nullBody.getId())
        );

        assertThat(bodies).hasSize(2);
        Map<UUID, String> byObservation = bodies
            .stream()
            .collect(Collectors.toMap(ObservationAdviceBody::getObservationId, ObservationAdviceBody::getBody));
        assertThat(byObservation)
            .containsEntry(delivered.getId(), "The advice the student saw")
            .containsEntry(failed.getId(), "The advice the direct post could not place")
            .doesNotContainKeys(prepared.getId(), suppressed.getId(), nullBody.getId());
        assertThat(bodies).allSatisfy(row -> assertThat(row.getFeedbackCreatedAt()).isNotNull());
    }

    @Test
    @DisplayName("deleting the parent Feedback cascades the join row away (ON DELETE CASCADE)")
    void deletingFeedbackCascadesJoinRow() {
        Feedback feedback = saveFeedback(0, FeedbackDeliveryState.DELIVERED, "Body");
        Observation observation = saveObservation("obs-cascade");
        bind(feedback, observation);
        assertThat(feedbackObservationRepository.findAll()).hasSize(1);

        feedbackRepository.deleteById(feedback.getId());
        feedbackRepository.flush();

        // The join row is gone, but the observation it pointed at survives (cascade is from feedback only).
        assertThat(feedbackObservationRepository.findAll()).isEmpty();
        assertThat(observationRepository.findById(observation.getId())).isPresent();
    }

    private void bind(Feedback feedback, Observation observation) {
        feedbackObservationRepository.insertIfAbsent(feedback.getId(), observation.getId(), "PRIMARY", 0);
    }

    private Feedback saveFeedback(int position, FeedbackDeliveryState state, String body) {
        return feedbackRepository.save(
            Feedback.builder()
                .agentJobId(agentJob.getId())
                .workspaceId(workspace.getId())
                .artifactType(WorkArtifact.PULL_REQUEST)
                .artifactId(42L)
                .recipientUserId(recipient.getId())
                .aboutUserId(recipient.getId())
                .channel(FeedbackChannel.IN_CONTEXT)
                .position(position)
                .deliveryState(state)
                .body(body)
                .source(FeedbackSource.AGENT)
                .createdAt(Instant.now())
                .deliveredAt(state == FeedbackDeliveryState.DELIVERED ? Instant.now() : null)
                .build()
        );
    }

    private Observation saveObservation(String occurrenceKey) {
        UUID id = UUID.randomUUID();
        observationRepository.insertIfAbsent(
            id,
            occurrenceKey,
            agentJob.getId(),
            practice.getId(),
            null,
            "PULL_REQUEST",
            42L,
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
