package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.Team;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.TeamRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.AreaStandingRow;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.PresenceCount;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.SeverityCount;
import de.tum.cit.aet.hephaestus.practices.observation.dto.DeveloperPracticeSummaryProjection;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceTeamRepositorySettings;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceTeamRepositorySettingsRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import tools.jackson.databind.ObjectMapper;

class ObservationRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeAreaRepository practiceAreaRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private IdentityProviderRepository gitProviderRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private WorkspaceTeamRepositorySettingsRepository workspaceTeamRepositorySettingsRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    private Workspace workspace;
    private Practice practice;
    private AgentJob agentJob;
    private User aboutUser;

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

        IdentityProvider provider = gitProviderRepository
            .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
            .orElseGet(() ->
                gitProviderRepository.save(new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com"))
            );
        aboutUser = TestUserFactory.createUser(100L, "test-about-user", provider);
        aboutUser = userRepository.save(aboutUser);
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
                null, // practiceRevisionId
                "PULL_REQUEST",
                42L,
                aboutUser.getId(),
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
            assertThat(found.getAssessment()).isEqualTo(Assessment.GOOD);
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
                null, // practiceRevisionId
                "PULL_REQUEST",
                1L,
                aboutUser.getId(),
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
                null, // practiceRevisionId
                "PULL_REQUEST",
                2L,
                aboutUser.getId(),
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
                null, // practiceRevisionId
                "PULL_REQUEST",
                99L,
                aboutUser.getId(),
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
            assertThat(found.getAssessment()).isEqualTo(Assessment.BAD);
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
                null, // practiceRevisionId
                "PULL_REQUEST",
                1L,
                aboutUser.getId(),
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
                null, // practiceRevisionId
                "PULL_REQUEST",
                1L,
                aboutUser.getId(),
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
                null, // practiceRevisionId
                "PULL_REQUEST",
                2L,
                aboutUser.getId(),
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
                null, // practiceRevisionId
                "PULL_REQUEST",
                1L,
                aboutUser.getId(),
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
                null, // practiceRevisionId
                "PULL_REQUEST",
                2L,
                aboutUser.getId(),
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
                aboutUser.getId(),
                workspace.getId()
            );

            assertThat(result).isEmpty();
        }

        @Test
        void aggregatesSinglePractice() {
            // Insert 3 ABSENT and 1 PRESENT for the same practice
            insertFinding("sum-1", practice, "ABSENT", Instant.parse("2026-03-18T10:00:00Z"));
            insertFinding("sum-2", practice, "ABSENT", Instant.parse("2026-03-19T10:00:00Z"));
            insertFinding("sum-3", practice, "ABSENT", Instant.parse("2026-03-20T14:30:00Z"));
            insertFinding("sum-4", practice, "PRESENT", Instant.parse("2026-03-17T08:00:00Z"));

            List<DeveloperPracticeSummary> result = observationRepository.findDeveloperPracticeSummary(
                aboutUser.getId(),
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
            assertThat(negative.getLastObservedAt()).isEqualTo(Instant.parse("2026-03-20T14:30:00Z"));

            DeveloperPracticeSummary positive = result
                .stream()
                .filter(s -> s.getPresence() == Presence.PRESENT)
                .findFirst()
                .orElseThrow();
            assertThat(positive.getCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("M6: a twice-reviewed target is not double-counted (latest-run dedup)")
        void countsOnlyLatestRunPerTarget() {
            // The SAME target (PR 7) reviewed in two runs: an earlier run said ABSENT/BAD, a later run said
            // PRESENT/GOOD. A naive COUNT/GROUP BY would surface 2 rows (1 ABSENT, 1 PRESENT); the dedup must
            // keep only the target's CURRENT state — a single PRESENT finding, count 1.
            AgentJob laterJob = new AgentJob();
            laterJob.setWorkspace(workspace);
            laterJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            laterJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            laterJob = agentJobRepository.save(laterJob);

            insertFindingForJob("m6-old", agentJob.getId(), 7L, "ABSENT", Instant.parse("2026-03-18T10:00:00Z"));
            insertFindingForJob("m6-new", laterJob.getId(), 7L, "PRESENT", Instant.parse("2026-03-20T10:00:00Z"));

            List<DeveloperPracticeSummary> result = observationRepository.findDeveloperPracticeSummary(
                aboutUser.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(1);
            DeveloperPracticeSummary row = result.get(0);
            assertThat(row.getPresence()).isEqualTo(Presence.PRESENT);
            assertThat(row.getCount()).isEqualTo(1);
            assertThat(row.getLastObservedAt()).isEqualTo(Instant.parse("2026-03-20T10:00:00Z"));
        }

        private void insertFindingForJob(String key, UUID jobId, long artifactId, String presence, Instant at) {
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                key,
                jobId,
                practice.getId(),
                null,
                "PULL_REQUEST",
                artifactId,
                aboutUser.getId(),
                "Test observation",
                presence,
                "PRESENT".equals(presence) ? "GOOD" : "BAD",
                "INFO",
                0.9f,
                null,
                null,
                null,
                at
            );
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
                aboutUser.getId(),
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
            // Finding in other workspace (same about-user)
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                "iso-2",
                otherJob.getId(),
                otherPractice.getId(),
                null, // practiceRevisionId
                "PULL_REQUEST",
                2L,
                aboutUser.getId(),
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
                aboutUser.getId(),
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
                aboutUser.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getLastObservedAt()).isEqualTo(latest);
        }

        @Test
        void aboutUserIsolation() {
            IdentityProvider provider = gitProviderRepository
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseThrow();
            User otherAboutUser = TestUserFactory.createUser(200L, "other-about-user", provider);
            otherAboutUser = userRepository.save(otherAboutUser);

            // Observation for target about-user
            insertFinding("contrib-iso-1", practice, "ABSENT", Instant.parse("2026-03-20T10:00:00Z"));
            // Observation for other about-user (same practice, same workspace)
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                "contrib-iso-2",
                agentJob.getId(),
                practice.getId(),
                null, // practiceRevisionId
                "PULL_REQUEST",
                2L,
                otherAboutUser.getId(),
                "Other about-user observation",
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
                aboutUser.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCount()).isEqualTo(1);
        }

        /** Helper to insert an observation with minimal boilerplate. */
        private void insertFinding(
            String idempotencyKey,
            Practice targetPractice,
            String presence,
            Instant observedAt
        ) {
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                idempotencyKey,
                agentJob.getId(),
                targetPractice.getId(),
                null, // practiceRevisionId
                "PULL_REQUEST",
                1L,
                aboutUser.getId(),
                "Test observation",
                presence,
                assessmentFor(presence),
                "INFO",
                0.9f,
                null,
                null,
                null,
                observedAt
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

        private void insertForJob(String key, UUID jobId, long artifactId, String presence, Instant observedAt) {
            // Former-GOOD practice valence: PRESENT -> GOOD (strength), ABSENT -> BAD (problem). A
            // NOT_APPLICABLE observation has no sign at all (assessment + severity are null).
            boolean notApplicable = "NOT_APPLICABLE".equals(presence);
            String assessment = notApplicable ? null : ("PRESENT".equals(presence) ? "GOOD" : "BAD");
            String severity = notApplicable ? null : "INFO";
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                key,
                jobId,
                practice.getId(),
                null,
                "PULL_REQUEST",
                artifactId,
                aboutUser.getId(),
                "finding",
                presence,
                assessment,
                severity,
                0.9f,
                null,
                null,
                null,
                observedAt
            );
        }

        @Test
        @DisplayName("dashboard summary counts only the latest run per target (re-review dedup)")
        void countsOnlyLatestRunPerArtifact() {
            // The SAME target (PR 42) reviewed twice: an earlier run said ABSENT/BAD, a later run said PRESENT/GOOD.
            // A naive COUNT would show 2 observations (1 PRESENT, 1 ABSENT); the dashboard must show the
            // target's CURRENT state only — 1 observation, PRESENT/GOOD.
            AgentJob laterJob = anotherJob();
            insertForJob("dedup-old", agentJob.getId(), 42L, "ABSENT", Instant.parse("2026-03-18T10:00:00Z"));
            insertForJob("dedup-new", laterJob.getId(), 42L, "PRESENT", Instant.parse("2026-03-20T10:00:00Z"));

            List<DeveloperPracticeSummaryProjection> result = observationRepository.findSummaryByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(1);
            DeveloperPracticeSummaryProjection row = result.get(0);
            assertThat(row.getPracticeSlug()).isEqualTo("test-practice");
            assertThat(row.getTotalObservations()).isEqualTo(1L);
            assertThat(row.getGoodCount()).isEqualTo(1L);
            assertThat(row.getBadCount()).isEqualTo(0L);
            assertThat(row.getLastObservedAt()).isEqualTo(Instant.parse("2026-03-20T10:00:00Z"));
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

            List<DeveloperPracticeSummaryProjection> result = observationRepository.findSummaryByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(1);
            DeveloperPracticeSummaryProjection row = result.get(0);
            assertThat(row.getTotalObservations()).isEqualTo(2L);
            assertThat(row.getGoodCount()).isEqualTo(1L);
            assertThat(row.getBadCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("NOT_APPLICABLE inflates totalObservations but never good/bad, and is omitted from findRecent")
        void notApplicableCountedInTotalButExcludedFromRecent() {
            // A NOT_APPLICABLE observation (no assessment, no severity) for a distinct target so the latest-run
            // dedup keeps it: it must count toward totalObservations yet contribute to neither good nor bad
            // (so total != good + bad by design), and the mentor's drill-down list must omit it entirely.
            insertForJob("na-target", agentJob.getId(), 50L, "NOT_APPLICABLE", Instant.parse("2026-03-20T10:00:00Z"));
            insertForJob("bad-target", agentJob.getId(), 51L, "ABSENT", Instant.parse("2026-03-20T11:00:00Z"));

            List<DeveloperPracticeSummaryProjection> summary = observationRepository.findSummaryByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId()
            );

            assertThat(summary).hasSize(1);
            DeveloperPracticeSummaryProjection row = summary.get(0);
            assertThat(row.getTotalObservations()).isEqualTo(2L); // NA + BAD both counted
            assertThat(row.getGoodCount()).isEqualTo(0L);
            assertThat(row.getBadCount()).isEqualTo(1L); // only the BAD row, the NA is excluded

            List<Observation> recent = observationRepository.findRecentByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId(),
                Instant.parse("2026-01-01T00:00:00Z"),
                PageRequest.of(0, 50)
            );

            // The NA row is filtered out of the drill-down list; only the actionable BAD finding remains.
            assertThat(recent).hasSize(1);
            assertThat(recent.get(0).getOccurrenceKey()).isEqualTo("bad-target");
            assertThat(recent.get(0).getPresence()).isEqualTo(Presence.ABSENT);
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
                null, // practiceRevisionId
                "PULL_REQUEST",
                1L,
                aboutUser.getId(),
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

    @Nested
    class AreaStandingCorroborationTests {

        /**
         * The native COUNT(DISTINCT artifact_id) / MAX(confidence) the standing floor (P4) keys on must be
         * computed correctly against real Postgres — a unit test mocks the row, so the SQL itself is only
         * exercised here. Two distinct PRs flagged BAD on the same area → distinctTargets=2; the max
         * confidence across the group is surfaced for the quarantine floor.
         */
        @Test
        @DisplayName("area-standing row carries COUNT(DISTINCT target) and MAX(confidence) for the P4 floor")
        void areaStandingExposesDistinctTargetsAndMaxConfidence() {
            PracticeArea area = new PracticeArea();
            area.setWorkspace(workspace);
            area.setSlug("robust-error-handling");
            area.setName("Handling failure robustly");
            area = practiceAreaRepository.save(area);
            practice.setArea(area);
            practice = practiceRepository.save(practice);

            Instant since = Instant.parse("2026-01-01T00:00:00Z");
            // Same BAD/MAJOR gap on two distinct PRs (artifact 10 and 11), differing confidence.
            insertAreaFinding("as-1", 10L, "ABSENT", "BAD", "MAJOR", 0.4f, Instant.parse("2026-03-20T10:00:00Z"));
            insertAreaFinding("as-2", 11L, "ABSENT", "BAD", "MAJOR", 0.7f, Instant.parse("2026-03-21T10:00:00Z"));

            List<AreaStandingRow> rows = observationRepository.findAreaStandingByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId(),
                since,
                since
            );

            AreaStandingRow bad = rows
                .stream()
                .filter(r -> r.getAssessment() == Assessment.BAD)
                .findFirst()
                .orElseThrow();
            assertThat(bad.getCount()).isEqualTo(2L);
            assertThat(bad.getDistinctTargets()).isEqualTo(2L);
            assertThat(bad.getMaxConfidence()).isEqualTo(0.7f);
        }

        /**
         * Pins the recent-window arithmetic: {@code recentCount} comes from a separate {@code :recentSince}
         * bind, so it must count only findings at-or-after that cutoff while {@code count} spans the whole
         * {@code :since} look-back. With {@code recentSince} strictly later than {@code since} and one of two
         * findings falling before it, recentCount must be 1 while count is 2 — a regression that reused
         * {@code :since} for the SUM (or dropped the :recentSince bind) would make recentCount == count and be
         * caught here. The mentor standing floor (P4) keys on this distinction.
         */
        @Test
        @DisplayName("area-standing recentCount counts only the recent window, not the full look-back")
        void areaStandingRecentCountHonoursSeparateRecentSince() {
            PracticeArea area = new PracticeArea();
            area.setWorkspace(workspace);
            area.setSlug("robust-error-handling");
            area.setName("Handling failure robustly");
            area = practiceAreaRepository.save(area);
            practice.setArea(area);
            practice = practiceRepository.save(practice);

            Instant since = Instant.parse("2026-01-01T00:00:00Z");
            Instant recentSince = Instant.parse("2026-03-15T00:00:00Z");
            // One BAD before the recent window (still inside the full look-back) and one inside it, on two
            // distinct targets.
            insertAreaFinding("rc-old", 20L, "ABSENT", "BAD", "MAJOR", 0.5f, Instant.parse("2026-02-01T10:00:00Z"));
            insertAreaFinding("rc-new", 21L, "ABSENT", "BAD", "MAJOR", 0.6f, Instant.parse("2026-03-20T10:00:00Z"));

            List<AreaStandingRow> rows = observationRepository.findAreaStandingByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId(),
                since,
                recentSince
            );

            AreaStandingRow bad = rows
                .stream()
                .filter(r -> r.getAssessment() == Assessment.BAD)
                .findFirst()
                .orElseThrow();
            assertThat(bad.getCount()).isEqualTo(2L);
            assertThat(bad.getRecentCount()).isEqualTo(1L); // only rc-new is at/after recentSince
            assertThat(bad.getDistinctTargets()).isEqualTo(2L);
        }

        private void insertAreaFinding(
            String key,
            long artifactId,
            String presence,
            String assessment,
            String severity,
            float confidence,
            Instant at
        ) {
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                key,
                agentJob.getId(),
                practice.getId(),
                null,
                "PULL_REQUEST",
                artifactId,
                aboutUser.getId(),
                "Area standing finding",
                presence,
                assessment,
                severity,
                confidence,
                null,
                null,
                null,
                at
            );
        }
    }

    @Nested
    class LatestRunTiebreakTests {

        private AgentJob anotherJob() {
            AgentJob job = new AgentJob();
            job.setWorkspace(workspace);
            job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            return agentJobRepository.save(job);
        }

        private void insert(String key, UUID jobId, long artifactId, String presence, Instant at) {
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                key,
                jobId,
                practice.getId(),
                null,
                "PULL_REQUEST",
                artifactId,
                aboutUser.getId(),
                "Tiebreak observation",
                presence,
                "PRESENT".equals(presence) ? "GOOD" : "BAD",
                "INFO",
                0.9f,
                null,
                null,
                null,
                at
            );
        }

        /**
         * Two runs sharing an identical {@code observed_at} on the same target: without the
         * {@code agent_job_id DESC} tiebreak the latest-run subquery's {@code LIMIT 1} pick is
         * plan-dependent, so the surfaced counts could flip between reads. The higher
         * {@code agent_job_id} (Postgres compares UUIDs as unsigned bytes — equal to hex-string
         * order, NOT Java's signed {@code UUID.compareTo}) must win on every dedup query.
         */
        @Test
        @DisplayName("equal observed_at timestamps tiebreak on agent_job_id, deterministically")
        void tiebreaksEqualTimestampsByAgentJobId() {
            AgentJob jobA = anotherJob();
            AgentJob jobB = anotherJob();
            AgentJob winner = jobA.getId().toString().compareTo(jobB.getId().toString()) > 0 ? jobA : jobB;
            AgentJob loser = winner == jobA ? jobB : jobA;

            Instant sameInstant = Instant.parse("2026-03-20T10:00:00Z");
            insert("tb-loser", loser.getId(), 42L, "ABSENT", sameInstant);
            insert("tb-winner", winner.getId(), 42L, "PRESENT", sameInstant);

            List<DeveloperPracticeSummaryProjection> summary = observationRepository.findSummaryByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId()
            );
            assertThat(summary).hasSize(1);
            assertThat(summary.get(0).getTotalObservations()).isEqualTo(1L);
            assertThat(summary.get(0).getGoodCount()).isEqualTo(1L);
            assertThat(summary.get(0).getBadCount()).isEqualTo(0L);

            // The sibling histogram query must agree on which run is "latest".
            List<PresenceCount> presences = observationRepository.countByPresenceForDeveloper(
                aboutUser.getId(),
                workspace.getId(),
                Instant.parse("2026-01-01T00:00:00Z")
            );
            assertThat(presences).hasSize(1);
            assertThat(presences.get(0).getPresence()).isEqualTo(Presence.PRESENT);
            assertThat(presences.get(0).getCount()).isEqualTo(1L);
        }
    }

    @Nested
    class HiddenRepositoryExclusionTests {

        /**
         * Every observation-serving AGGREGATE must exclude observations whose artifact lives in a
         * repository that any team's settings mark {@code hidden_from_contributions} (fail-closed:
         * these queries carry no viewing-team context, so hidden for one team means hidden for
         * everyone — see {@code findSummaryByDeveloperAndWorkspace}). One BAD observation on a
         * visible-repo PR and one on a hidden-repo PR: only the visible one may reach the aggregates.
         */
        @Test
        @DisplayName("observations on hidden-repository artifacts are excluded from all aggregate serving queries")
        void excludesHiddenRepositoryObservationsOnAggregateServingQueries() {
            PracticeArea area = new PracticeArea();
            area.setWorkspace(workspace);
            area.setSlug("robust-error-handling");
            area.setName("Handling failure robustly");
            area = practiceAreaRepository.save(area);
            practice.setArea(area);
            practice = practiceRepository.save(practice);

            PullRequest visiblePr = persistPullRequest("test-org/visible-repo", 201L, false);
            PullRequest hiddenPr = persistPullRequest("test-org/hidden-repo", 202L, true);
            insertBad("visible-repo-bad", visiblePr.getId(), Instant.parse("2026-03-20T10:00:00Z"));
            insertBad("hidden-repo-bad", hiddenPr.getId(), Instant.parse("2026-03-20T11:00:00Z"));

            Instant since = Instant.parse("2026-01-01T00:00:00Z");

            List<DeveloperPracticeSummaryProjection> summary = observationRepository.findSummaryByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId()
            );
            assertThat(summary).hasSize(1);
            assertThat(summary.get(0).getTotalObservations()).isEqualTo(1L);
            assertThat(summary.get(0).getLastObservedAt()).isEqualTo(Instant.parse("2026-03-20T10:00:00Z"));

            List<DeveloperPracticeSummary> practiceSummary = observationRepository.findDeveloperPracticeSummary(
                aboutUser.getId(),
                workspace.getId()
            );
            assertThat(practiceSummary).hasSize(1);
            assertThat(practiceSummary.get(0).getCount()).isEqualTo(1L);

            List<Observation> recent = observationRepository.findRecentByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId(),
                since,
                PageRequest.of(0, 50)
            );
            assertThat(recent).extracting(Observation::getArtifactId).containsExactly(visiblePr.getId());

            List<SeverityCount> severities = observationRepository.countBySeverityForDeveloper(
                aboutUser.getId(),
                workspace.getId(),
                since
            );
            assertThat(severities).hasSize(1);
            assertThat(severities.get(0).getSeverity()).isEqualTo(Severity.MAJOR);
            assertThat(severities.get(0).getCount()).isEqualTo(1L);

            List<PresenceCount> presences = observationRepository.countByPresenceForDeveloper(
                aboutUser.getId(),
                workspace.getId(),
                since
            );
            assertThat(presences).hasSize(1);
            assertThat(presences.get(0).getCount()).isEqualTo(1L);

            List<AreaStandingRow> standing = observationRepository.findAreaStandingByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId(),
                since,
                since
            );
            assertThat(standing).hasSize(1);
            assertThat(standing.get(0).getCount()).isEqualTo(1L);
            assertThat(standing.get(0).getDistinctTargets()).isEqualTo(1L);
        }

        private void insertBad(String key, long artifactId, Instant at) {
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                key,
                agentJob.getId(),
                practice.getId(),
                null,
                "PULL_REQUEST",
                artifactId,
                aboutUser.getId(),
                "Hidden-repo exclusion observation",
                "ABSENT",
                "BAD",
                "MAJOR",
                0.9f,
                null,
                null,
                null,
                at
            );
        }

        private PullRequest persistPullRequest(String nameWithOwner, long nativeId, boolean hiddenFromContributions) {
            IdentityProvider provider = gitProviderRepository
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseThrow();

            Repository repo = new Repository();
            repo.setNativeId(nativeId);
            repo.setProvider(provider);
            repo.setName(nameWithOwner.substring(nameWithOwner.indexOf('/') + 1));
            repo.setNameWithOwner(nameWithOwner);
            repo.setHtmlUrl("https://github.com/" + nameWithOwner);
            repo.setDefaultBranch("main");
            repo.setCreatedAt(Instant.now());
            repo.setUpdatedAt(Instant.now());
            repo.setPushedAt(Instant.now());
            repo = repositoryRepository.save(repo);

            if (hiddenFromContributions) {
                Team team = new Team();
                team.setNativeId(nativeId);
                team.setProvider(provider);
                team.setName("team-" + nativeId);
                team.setSlug("team-" + nativeId);
                team.setPrivacy(Team.Privacy.VISIBLE);
                team = teamRepository.save(team);

                WorkspaceTeamRepositorySettings settings = new WorkspaceTeamRepositorySettings(workspace, team, repo);
                settings.setHiddenFromContributions(true);
                workspaceTeamRepositorySettingsRepository.save(settings);
            }

            PullRequest pr = new PullRequest();
            pr.setNativeId(nativeId);
            pr.setProvider(provider);
            pr.setNumber((int) nativeId);
            pr.setTitle("PR " + nativeId);
            pr.setState(PullRequest.State.OPEN);
            pr.setRepository(repo);
            pr.setCreatedAt(Instant.now());
            pr.setUpdatedAt(Instant.now());
            return pullRequestRepository.save(pr);
        }
    }
}
