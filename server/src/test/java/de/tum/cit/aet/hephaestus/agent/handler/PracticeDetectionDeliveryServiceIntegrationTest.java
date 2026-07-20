package de.tum.cit.aet.hephaestus.agent.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.agent.handler.PracticeDetectionResultParser.ValidatedFinding;
import de.tum.cit.aet.hephaestus.agent.handler.spi.JobDeliveryException;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.practices.observation.PracticeDetectionCompletedEvent;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Integration test for {@link PracticeDetectionDeliveryService} exercising real PostgreSQL
 * for finding persistence (INSERT ... ON CONFLICT DO NOTHING), negative cap enforcement,
 * observation classification, and {@link PracticeDetectionCompletedEvent} publication.
 *
 * <p>No mocks required — this service layer does not call external APIs. It resolves practice
 * slugs against the DB and persists findings via {@code ObservationRepository.insertIfAbsent()}.
 */
@RecordApplicationEvents
class PracticeDetectionDeliveryServiceIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private PracticeDetectionDeliveryService deliveryService;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeRevisionRepository practiceRevisionRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private IdentityProviderRepository gitProviderRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    private Workspace workspace;
    private AgentJob agentJob;
    private User developer;
    private Long prId;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("delivery-test"));

        createPractice("pr-description-quality", "PR Description Quality");
        createPractice("error-handling", "Error Handling");

        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName("delivery-config");
        config.setEnabled(true);
        config.setLlmProvider(LlmProvider.ANTHROPIC);
        config.setTimeoutSeconds(300);
        config = agentConfigRepository.save(config);

        agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setConfig(config);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob = agentJobRepository.save(agentJob);

        IdentityProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseGet(() ->
                gitProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
            );
        developer = TestUserFactory.createUser(200L, "test-pr-author", provider);
        developer = userRepository.save(developer);

        Repository repo = new Repository();
        repo.setNativeId(1001L);
        repo.setProvider(provider);
        repo.setName("test-repo");
        repo.setNameWithOwner("org/test-repo");
        repo.setHtmlUrl("https://github.com/org/test-repo");
        repo.setDefaultBranch("main");
        repo = repositoryRepository.save(repo);

        Instant now = Instant.now();
        pullRequestRepository.upsertCore(
            5001L,
            provider.getId(),
            42,
            "Test PR",
            "Test body",
            "OPEN",
            null,
            "https://github.com/org/test-repo/pull/42",
            false,
            null,
            0,
            now,
            now,
            now,
            developer.getId(),
            repo.getId(),
            null,
            null,
            false,
            false,
            1,
            10,
            5,
            3,
            null,
            null,
            null,
            "feature/test",
            "main",
            "abc123",
            "def456",
            null,
            null // mergeCommitSha
        );
        prId = pullRequestRepository.findByRepositoryIdAndNumber(repo.getId(), 42).orElseThrow().getId();

        ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
        metadata.put("pull_request_id", prId);
        agentJob.setMetadata(metadata);
        agentJob = agentJobRepository.save(agentJob);
    }

    private Practice createPractice(String slug, String name) {
        Practice p = new Practice();
        p.setWorkspace(workspace);
        p.setSlug(slug);
        p.setName(name);
        p.setCriteria("Test " + slug);
        p.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
        return practiceRepository.save(p);
    }

    /**
     * Build a finding whose valence follows the former-GOOD practice convention used by these
     * fixtures (pr-description-quality, error-handling): PRESENT→GOOD, ABSENT→BAD, NOT_APPLICABLE→null.
     */
    private ValidatedFinding finding(String slug, Presence presence) {
        Assessment assessment = switch (presence) {
            case PRESENT -> Assessment.GOOD;
            case ABSENT -> Assessment.BAD;
            case NOT_APPLICABLE -> null;
        };
        return new ValidatedFinding(
            slug,
            "Test: " + slug,
            presence,
            assessment,
            Severity.INFO,
            0.9f,
            null,
            null,
            null,
            List.of()
        );
    }

    @Nested
    class EndToEnd {

        @Test
        void validFindingsPersistedToDb() {
            var findings = List.of(
                finding("pr-description-quality", Presence.PRESENT),
                finding("error-handling", Presence.ABSENT)
            );

            var result = deliveryService.deliver(agentJob, findings);

            assertThat(result.inserted()).isEqualTo(2);
            assertThat(result.discardedUnknownSlug()).isZero();
            assertThat(result.hasNegative()).isTrue();

            List<Observation> persisted = observationRepository.findAll();
            assertThat(persisted).hasSize(2);
            assertThat(persisted)
                .extracting(Observation::getPresence)
                .containsExactlyInAnyOrder(Presence.PRESENT, Presence.ABSENT);
            assertThat(persisted)
                .extracting(Observation::getConfidence)
                .allMatch(c -> c == 0.9f);
        }

        @Test
        @DisplayName("returned observationKeys align exactly with the persisted recurrence_key set")
        void returnedFingerprintsMatchPersistedRecurrenceKeys() {
            var findings = List.of(
                finding("pr-description-quality", Presence.PRESENT),
                finding("error-handling", Presence.ABSENT)
            );

            var result = deliveryService.deliver(agentJob, findings);

            // The map the service returns is the contract the delivery layer keys feedback supersession on;
            // it MUST be the same value written to observation.recurrence_key, or supersession breaks.
            assertThat(result.observationKeys().values().stream().map(ObservationKeys::recurrenceKey).toList())
                .as("one stable key returned per delivered finding")
                .hasSize(2)
                .allMatch(k -> k != null && k.matches("[0-9a-f]{64}"));

            List<String> persistedKeys = observationRepository
                .findAll()
                .stream()
                .map(Observation::getRecurrenceKey)
                .toList();
            assertThat(persistedKeys)
                .as("every returned fingerprint is persisted as a recurrence_key, and vice versa")
                .containsExactlyInAnyOrderElementsOf(
                    result.observationKeys().values().stream().map(ObservationKeys::recurrenceKey).toList()
                );
        }

        @Test
        @DisplayName("re-delivering same job creates no duplicates")
        void idempotentRedelivery() {
            var findings = List.of(finding("pr-description-quality", Presence.PRESENT));

            var first = deliveryService.deliver(agentJob, findings);
            var second = deliveryService.deliver(agentJob, findings);

            assertThat(first.inserted()).isEqualTo(1);
            assertThat(second.inserted()).isZero();
            assertThat(second.discardedDuplicate()).isEqualTo(1);
            assertThat(observationRepository.findAll()).hasSize(1);
        }
    }

    @Nested
    class PracticeResolution {

        @Test
        void unknownSlugsSkipped() {
            var findings = List.of(
                finding("pr-description-quality", Presence.PRESENT),
                finding("nonexistent-practice", Presence.PRESENT)
            );

            var result = deliveryService.deliver(agentJob, findings);

            assertThat(result.inserted()).isEqualTo(1);
            assertThat(result.discardedUnknownSlug()).isEqualTo(1);
            assertThat(observationRepository.findAll()).hasSize(1);
        }
    }

    @Nested
    class RevisionPinning {

        @Test
        @DisplayName("persisted finding pins the practice's current criteria revision (SCD-2)")
        void findingPinsCurrentRevision() {
            // The seeded practice was saved straight through the repository, so it has no revision yet.
            // Append revision 1 (the ostensive-as-authored) exactly as PracticeService.createPractice would.
            Practice practice = practiceRepository
                .findByWorkspaceIdAndSlug(workspace.getId(), "pr-description-quality")
                .orElseThrow();
            PracticeRevision revision = practiceRevisionRepository.save(
                new PracticeRevision(practice, 1, practice.getCriteria())
            );

            var findings = List.of(finding("pr-description-quality", Presence.PRESENT));

            var result = deliveryService.deliver(agentJob, findings);

            assertThat(result.inserted()).isEqualTo(1);

            List<Observation> persisted = observationRepository.findAll();
            assertThat(persisted).hasSize(1);
            // The delivery service looks up the current revision per practice and passes practiceRevisionId
            // to insertIfAbsent — so the finding must pin to exactly that revision, not null.
            Observation only = persisted.get(0);
            assertThat(only.getPracticeRevision()).isNotNull();
            assertThat(only.getPracticeRevision().getId()).isEqualTo(revision.getId());
        }
    }

    @Nested
    class DistinctBadFindingsAllPersisted {

        @Test
        void persistsEveryDistinctBadFinding() {
            // Each finding gets a unique idempotency key (includes index), so all 7 are
            // distinct. There is NO per-practice cap on BAD findings: every distinct one
            // is persisted (none discarded as a duplicate).
            var findings = new ArrayList<ValidatedFinding>();
            for (int i = 0; i < 7; i++) {
                findings.add(
                    new ValidatedFinding(
                        "pr-description-quality",
                        "Negative finding " + i,
                        Presence.ABSENT,
                        Assessment.BAD,
                        Severity.MINOR,
                        0.8f,
                        null,
                        null,
                        null,
                        List.of()
                    )
                );
            }

            var result = deliveryService.deliver(agentJob, findings);

            assertThat(result.inserted()).isEqualTo(7);
            assertThat(result.discardedDuplicate()).isEqualTo(0);
            assertThat(observationRepository.findAll()).hasSize(7);
        }
    }

    @Nested
    class EventPublication {

        @Test
        void publishesEvent() {
            var findings = List.of(finding("pr-description-quality", Presence.PRESENT));

            deliveryService.deliver(agentJob, findings);

            List<PracticeDetectionCompletedEvent> events = applicationEvents
                .stream(PracticeDetectionCompletedEvent.class)
                .toList();
            assertThat(events).hasSize(1);
            PracticeDetectionCompletedEvent event = events.get(0);
            assertThat(event.agentJobId()).isEqualTo(agentJob.getId());
            assertThat(event.workspaceId()).isEqualTo(workspace.getId());
            assertThat(event.artifactType()).isEqualTo(WorkArtifact.PULL_REQUEST);
            assertThat(event.artifactId()).isEqualTo(prId);
            assertThat(event.findingsInserted()).isEqualTo(1);
            assertThat(event.findingsDiscarded()).isZero();
            assertThat(event.hasNegative()).isFalse();
            assertThat(event.developerId()).isEqualTo(developer.getId());
        }

        @Test
        void emptyFindingsPublishesZeroEvent() {
            var result = deliveryService.deliver(agentJob, List.of());

            assertThat(result.inserted()).isZero();
            assertThat(result.hasNegative()).isFalse();

            List<PracticeDetectionCompletedEvent> events = applicationEvents
                .stream(PracticeDetectionCompletedEvent.class)
                .toList();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).findingsInserted()).isZero();
            assertThat(events.get(0).findingsDiscarded()).isZero();
            assertThat(events.get(0).hasNegative()).isFalse();
        }
    }

    @Nested
    class NonNegativeObservations {

        @Test
        void positiveObservationsDoNotTriggerHasNegative() {
            var findings = List.of(
                finding("pr-description-quality", Presence.PRESENT),
                finding("error-handling", Presence.PRESENT)
            );

            var result = deliveryService.deliver(agentJob, findings);

            assertThat(result.inserted()).isEqualTo(2);
            assertThat(result.hasNegative()).isFalse();

            List<Observation> persisted = observationRepository.findAll();
            assertThat(persisted).hasSize(2);
            assertThat(persisted)
                .extracting(Observation::getPresence)
                .containsExactlyInAnyOrder(Presence.PRESENT, Presence.PRESENT);
        }
    }

    @Nested
    class ErrorCases {

        @Test
        void throwsWhenPrNotFound() {
            ObjectNode metadata = OBJECT_MAPPER.createObjectNode();
            metadata.put("pull_request_id", 999999L);
            agentJob.setMetadata(metadata);
            agentJob = agentJobRepository.save(agentJob);

            var findings = List.of(finding("pr-description-quality", Presence.PRESENT));

            assertThatThrownBy(() -> deliveryService.deliver(agentJob, findings))
                .isInstanceOf(JobDeliveryException.class)
                .hasMessageContaining("Pull request not found");
        }
    }
}
