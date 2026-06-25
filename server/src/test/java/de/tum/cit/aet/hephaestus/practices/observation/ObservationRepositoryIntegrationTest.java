package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.GitProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.observation.dto.DeveloperPracticeSummaryProjection;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.ObjectMapper;

class ObservationRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
    private GitProviderRepository gitProviderRepository;

    private Workspace workspace;
    private Practice practice;
    private AgentJob agentJob;
    private User developer;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("finding-test"));

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

        GitProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));
        developer = TestUserFactory.createUser(100L, "test-developer", provider);
        developer = userRepository.save(developer);
    }

    @Nested
    class InsertIfAbsentTests {

        @Test
        void insertsNewFinding() {
            UUID id = UUID.randomUUID();
            int result = observationRepository.insertIfAbsent(
                id,
                "key-1",
                agentJob.getId(),
                practice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                42L,
                developer.getId(),
                "Good PR description",
                "PRESENT",
                "GOOD",
                "INFO",
                0.95f,
                null,
                "Good quality",
                null,
                Instant.now()
            );

            assertThat(result).isEqualTo(1);

            Observation found = observationRepository.findById(id).orElseThrow();
            assertThat(found.getOccurrenceKey()).isEqualTo("key-1");
            assertThat(found.getTitle()).isEqualTo("Good PR description");
            assertThat(found.getPresence().name()).isEqualTo("PRESENT");
            assertThat(found.getSeverity().name()).isEqualTo("INFO");
            assertThat(found.getConfidence()).isEqualTo(0.95f);
            assertThat(found.getReasoning()).isEqualTo("Good quality");
        }

        @Test
        @DisplayName("returns 0 on duplicate idempotency key")
        void rejectsDuplicate() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            Instant now = Instant.now();

            int first = observationRepository.insertIfAbsent(
                id1,
                "dup-key",
                agentJob.getId(),
                practice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                1L,
                developer.getId(),
                "Duplicate test",
                "PRESENT",
                "GOOD",
                "INFO",
                0.8f,
                null,
                null,
                null,
                now
            );

            int second = observationRepository.insertIfAbsent(
                id2,
                "dup-key",
                agentJob.getId(),
                practice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                2L,
                developer.getId(),
                "Should not insert",
                "ABSENT",
                "BAD",
                "MAJOR",
                0.5f,
                null,
                null,
                null,
                now
            );

            assertThat(first).isEqualTo(1);
            assertThat(second).isEqualTo(0);
            assertThat(observationRepository.findAll()).hasSize(1);
        }

        @Test
        void insertsWithEvidence() {
            UUID id = UUID.randomUUID();
            String evidence = "{\"files\":[\"src/Main.java\"],\"diff_lines\":42}";

            int result = observationRepository.insertIfAbsent(
                id,
                "evidence-key",
                agentJob.getId(),
                practice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                99L,
                developer.getId(),
                "Missing error handling in Main.java",
                "ABSENT",
                "BAD",
                "MAJOR",
                0.7f,
                evidence,
                "Missing error handling",
                null,
                Instant.now()
            );

            assertThat(result).isEqualTo(1);

            Observation found = observationRepository.findById(id).orElseThrow();
            assertThat(found.getEvidence()).isNotNull();
            assertThat(found.getEvidence().get("files").get(0).asString()).isEqualTo("src/Main.java");
            assertThat(found.getEvidence().get("diff_lines").asInt()).isEqualTo(42);
        }
    }

    @Nested
    class WorkspacePurgeTests {

        @Test
        @DisplayName("deleteAllByPracticeWorkspaceId removes findings for workspace practices")
        void deletesFindings() {
            UUID id = UUID.randomUUID();
            observationRepository.insertIfAbsent(
                id,
                "purge-key",
                agentJob.getId(),
                practice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                1L,
                developer.getId(),
                "Purge test finding",
                "PRESENT",
                "GOOD",
                "INFO",
                0.9f,
                null,
                null,
                null,
                Instant.now()
            );
            assertThat(observationRepository.findAll()).hasSize(1);

            observationRepository.deleteAllByPracticeWorkspaceId(workspace.getId());

            assertThat(observationRepository.findAll()).isEmpty();
        }
    }

    @Nested
    class WorkspaceIsolationTests {

        @Test
        void purgeDoesNotAffectOtherWorkspace() {
            // Create workspace B with its own practice and finding
            Workspace workspaceB = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("ws-b"));
            Practice practiceB = new Practice();
            practiceB.setWorkspace(workspaceB);
            practiceB.setSlug("practice-b");
            practiceB.setName("Practice B");
            practiceB.setCriteria("Workspace B practice");
            practiceB.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            practiceB = practiceRepository.save(practiceB);

            AgentJob agentJobB = new AgentJob();
            agentJobB.setWorkspace(workspaceB);
            agentJobB.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            agentJobB.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            agentJobB = agentJobRepository.save(agentJobB);

            // Finding in workspace A
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                "ws-a-key",
                agentJob.getId(),
                practice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                1L,
                developer.getId(),
                "WS-A finding",
                "PRESENT",
                "GOOD",
                "INFO",
                0.9f,
                null,
                null,
                null,
                Instant.now()
            );
            // Finding in workspace B
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                "ws-b-key",
                agentJobB.getId(),
                practiceB.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                2L,
                developer.getId(),
                "WS-B finding",
                "ABSENT",
                "BAD",
                "MINOR",
                0.5f,
                null,
                null,
                null,
                Instant.now()
            );
            assertThat(observationRepository.findAll()).hasSize(2);

            // Purge workspace A only
            observationRepository.deleteAllByPracticeWorkspaceId(workspace.getId());

            // Workspace B's finding must survive
            List<Observation> remaining = observationRepository.findAll();
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getOccurrenceKey()).isEqualTo("ws-b-key");
        }
    }

    @Nested
    class CascadeDeleteTests {

        @Test
        void cascadesFromPracticeSelectively() {
            // Create a second practice with its own finding
            Practice otherPractice = new Practice();
            otherPractice.setWorkspace(workspace);
            otherPractice.setSlug("other-practice");
            otherPractice.setName("Other Practice");
            otherPractice.setCriteria("Other description");
            otherPractice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            otherPractice = practiceRepository.save(otherPractice);

            // Finding on the practice to be deleted
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                "cascade-key-1",
                agentJob.getId(),
                practice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                1L,
                developer.getId(),
                "Cascade test 1",
                "ABSENT",
                "BAD",
                "MAJOR",
                0.6f,
                null,
                null,
                null,
                Instant.now()
            );
            // Finding on the other practice (should survive)
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                "cascade-key-2",
                agentJob.getId(),
                otherPractice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                2L,
                developer.getId(),
                "Cascade test 2",
                "PRESENT",
                "GOOD",
                "INFO",
                0.9f,
                null,
                null,
                null,
                Instant.now()
            );
            assertThat(observationRepository.findAll()).hasSize(2);

            practiceRepository.deleteById(practice.getId());
            practiceRepository.flush();

            // Only the finding for the deleted practice should be gone
            List<Observation> remaining = observationRepository.findAll();
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getOccurrenceKey()).isEqualTo("cascade-key-2");
        }
    }

    @Nested
    class FindDeveloperPracticeSummaryTests {

        @Test
        void returnsEmptyForNoFindings() {
            List<DeveloperPracticeSummary> result = observationRepository.findDeveloperPracticeSummary(
                developer.getId(),
                workspace.getId()
            );

            assertThat(result).isEmpty();
        }

        @Test
        void aggregatesSinglePractice() {
            // Insert 3 NEGATIVE and 1 POSITIVE for the same practice
            insertFinding("sum-1", practice, "ABSENT", Instant.parse("2026-03-18T10:00:00Z"));
            insertFinding("sum-2", practice, "ABSENT", Instant.parse("2026-03-19T10:00:00Z"));
            insertFinding("sum-3", practice, "ABSENT", Instant.parse("2026-03-20T14:30:00Z"));
            insertFinding("sum-4", practice, "PRESENT", Instant.parse("2026-03-17T08:00:00Z"));

            List<DeveloperPracticeSummary> result = observationRepository.findDeveloperPracticeSummary(
                developer.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(2); // One row per (slug, observation) combination

            DeveloperPracticeSummary negative = result
                .stream()
                .filter(s -> s.getPresence() == Presence.ABSENT)
                .findFirst()
                .orElseThrow();
            assertThat(negative.getPracticeSlug()).isEqualTo("test-practice");
            assertThat(negative.getCount()).isEqualTo(3);
            assertThat(negative.getLastDetectedAt()).isEqualTo(Instant.parse("2026-03-20T14:30:00Z"));

            DeveloperPracticeSummary positive = result
                .stream()
                .filter(s -> s.getPresence() == Presence.PRESENT)
                .findFirst()
                .orElseThrow();
            assertThat(positive.getCount()).isEqualTo(1);
        }

        @Test
        void groupsByPracticeSlug() {
            Practice secondPractice = new Practice();
            secondPractice.setWorkspace(workspace);
            secondPractice.setSlug("error-handling");
            secondPractice.setName("Error Handling");
            secondPractice.setCriteria("Handle errors");
            secondPractice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            secondPractice = practiceRepository.save(secondPractice);

            insertFinding("multi-1", practice, "PRESENT", Instant.parse("2026-03-20T10:00:00Z"));
            insertFinding("multi-2", secondPractice, "ABSENT", Instant.parse("2026-03-19T10:00:00Z"));

            List<DeveloperPracticeSummary> result = observationRepository.findDeveloperPracticeSummary(
                developer.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(2);
            assertThat(result)
                .extracting(DeveloperPracticeSummary::getPracticeSlug)
                .containsExactlyInAnyOrder("test-practice", "error-handling");
        }

        @Test
        void workspaceIsolation() {
            Workspace otherWorkspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("other-ws"));
            Practice otherPractice = new Practice();
            otherPractice.setWorkspace(otherWorkspace);
            otherPractice.setSlug("test-practice"); // Same slug, different workspace
            otherPractice.setName("Test Practice");
            otherPractice.setCriteria("Other workspace practice");
            otherPractice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            otherPractice = practiceRepository.save(otherPractice);

            AgentJob otherJob = new AgentJob();
            otherJob.setWorkspace(otherWorkspace);
            otherJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            otherJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            otherJob = agentJobRepository.save(otherJob);

            // Finding in target workspace
            insertFinding("iso-1", practice, "ABSENT", Instant.parse("2026-03-20T10:00:00Z"));
            // Finding in other workspace (same developer)
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                "iso-2",
                otherJob.getId(),
                otherPractice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                2L,
                developer.getId(),
                "Other WS finding",
                "ABSENT",
                "BAD",
                "MAJOR",
                0.8f,
                null,
                null,
                null,
                Instant.parse("2026-03-20T10:00:00Z")
            );

            List<DeveloperPracticeSummary> result = observationRepository.findDeveloperPracticeSummary(
                developer.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPracticeSlug()).isEqualTo("test-practice");
            assertThat(result.get(0).getCount()).isEqualTo(1);
        }

        @Test
        void correctLastDetectedAt() {
            Instant earliest = Instant.parse("2026-03-15T08:00:00Z");
            Instant latest = Instant.parse("2026-03-20T14:30:00Z");
            Instant middle = Instant.parse("2026-03-18T12:00:00Z");

            insertFinding("time-1", practice, "ABSENT", earliest);
            insertFinding("time-2", practice, "ABSENT", latest);
            insertFinding("time-3", practice, "ABSENT", middle);

            List<DeveloperPracticeSummary> result = observationRepository.findDeveloperPracticeSummary(
                developer.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getLastDetectedAt()).isEqualTo(latest);
        }

        @Test
        void developerIsolation() {
            GitProvider provider = gitProviderRepository
                .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
                .orElseThrow();
            User otherDeveloper = TestUserFactory.createUser(200L, "other-developer", provider);
            otherDeveloper = userRepository.save(otherDeveloper);

            // Finding for target developer
            insertFinding("contrib-iso-1", practice, "ABSENT", Instant.parse("2026-03-20T10:00:00Z"));
            // Finding for other developer (same practice, same workspace)
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                "contrib-iso-2",
                agentJob.getId(),
                practice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                2L,
                otherDeveloper.getId(),
                "Other developer finding",
                "ABSENT",
                "BAD",
                "MAJOR",
                0.8f,
                null,
                null,
                null,
                Instant.parse("2026-03-20T10:00:00Z")
            );

            List<DeveloperPracticeSummary> result = observationRepository.findDeveloperPracticeSummary(
                developer.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCount()).isEqualTo(1);
        }

        /** Helper to insert a finding with minimal boilerplate. */
        private void insertFinding(
            String idempotencyKey,
            Practice targetPractice,
            String presence,
            Instant detectedAt
        ) {
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                idempotencyKey,
                agentJob.getId(),
                targetPractice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                1L,
                developer.getId(),
                "Test finding",
                presence,
                assessmentFor(presence),
                "INFO",
                0.9f,
                null,
                null,
                null,
                detectedAt
            );
        }

        /** Former-GOOD practice valence: PRESENT is a strength (GOOD), ABSENT is a problem (BAD). */
        private static String assessmentFor(String presence) {
            return "PRESENT".equals(presence) ? "GOOD" : "BAD";
        }
    }

    @Nested
    class FindSummaryDashboardDedupTests {

        private AgentJob anotherJob() {
            AgentJob job = new AgentJob();
            job.setWorkspace(workspace);
            job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            return agentJobRepository.save(job);
        }

        private void insertForJob(String key, UUID jobId, long artifactId, String presence, Instant detectedAt) {
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                key,
                jobId,
                practice.getId(),
                null,
                "PULL_REQUEST",
                artifactId,
                developer.getId(),
                "finding",
                presence,
                // Former-GOOD practice valence: PRESENT -> GOOD (strength), ABSENT -> BAD (problem).
                "PRESENT".equals(presence) ? "GOOD" : "BAD",
                "INFO",
                0.9f,
                null,
                null,
                null,
                detectedAt
            );
        }

        @Test
        @DisplayName("dashboard summary counts only the latest run per target (re-review dedup)")
        void countsOnlyLatestRunPerArtifact() {
            // The SAME target (PR 42) reviewed twice: an earlier run said ABSENT/BAD, a later run said PRESENT/GOOD.
            // A naive COUNT would show 2 findings (1 observed, 1 not-observed); the dashboard must show the
            // target's CURRENT state only — 1 finding, observed.
            AgentJob laterJob = anotherJob();
            insertForJob("dedup-old", agentJob.getId(), 42L, "ABSENT", Instant.parse("2026-03-18T10:00:00Z"));
            insertForJob("dedup-new", laterJob.getId(), 42L, "PRESENT", Instant.parse("2026-03-20T10:00:00Z"));

            List<DeveloperPracticeSummaryProjection> result =
                observationRepository.findSummaryByDeveloperAndWorkspace(developer.getId(), workspace.getId());

            assertThat(result).hasSize(1);
            DeveloperPracticeSummaryProjection row = result.get(0);
            assertThat(row.getPracticeSlug()).isEqualTo("test-practice");
            assertThat(row.getTotalFindings()).isEqualTo(1L);
            assertThat(row.getGoodCount()).isEqualTo(1L);
            assertThat(row.getBadCount()).isEqualTo(0L);
            assertThat(row.getLastFindingAt()).isEqualTo(Instant.parse("2026-03-20T10:00:00Z"));
        }

        @Test
        @DisplayName("each distinct target contributes its own latest run")
        void countsEachTargetIndependently() {
            // Target 42 reviewed twice (latest = PRESENT/GOOD); target 43 reviewed once (ABSENT/BAD). The dedup is
            // per-target, so the older run survives for 43 while only the newer run survives for 42.
            AgentJob laterJob = anotherJob();
            insertForJob("t42-old", agentJob.getId(), 42L, "ABSENT", Instant.parse("2026-03-18T10:00:00Z"));
            insertForJob("t42-new", laterJob.getId(), 42L, "PRESENT", Instant.parse("2026-03-20T10:00:00Z"));
            insertForJob("t43", agentJob.getId(), 43L, "ABSENT", Instant.parse("2026-03-19T10:00:00Z"));

            List<DeveloperPracticeSummaryProjection> result =
                observationRepository.findSummaryByDeveloperAndWorkspace(developer.getId(), workspace.getId());

            assertThat(result).hasSize(1);
            DeveloperPracticeSummaryProjection row = result.get(0);
            assertThat(row.getTotalFindings()).isEqualTo(2L);
            assertThat(row.getGoodCount()).isEqualTo(1L);
            assertThat(row.getBadCount()).isEqualTo(1L);
        }
    }

    @Nested
    class ArtifactTypeTests {

        @Test
        @DisplayName("persisted 'PULL_REQUEST' maps to WorkArtifact.PULL_REQUEST on read")
        void enumRoundTrip() {
            UUID id = UUID.randomUUID();
            observationRepository.insertIfAbsent(
                id,
                "tt-roundtrip",
                agentJob.getId(),
                practice.getId(),
                null, // practiceRevisionId — pre-versioning marker
                "PULL_REQUEST",
                1L,
                developer.getId(),
                "Enum mapping test",
                "PRESENT",
                "GOOD",
                "INFO",
                0.9f,
                null,
                null,
                null,
                Instant.now()
            );

            Observation found = observationRepository.findById(id).orElseThrow();
            assertThat(found.getArtifactType()).isEqualTo(WorkArtifact.PULL_REQUEST);
        }
    }
}
