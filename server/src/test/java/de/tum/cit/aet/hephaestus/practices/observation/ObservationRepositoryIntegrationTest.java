package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.Team;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.TeamRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
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
import org.springframework.dao.DataIntegrityViolationException;
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
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        practice.setWorkspace(workspace);
        practice.setSlug("test-practice");
        practice.setName("Test Practice");
        practice.setCriteria("Test description");
        practice.setBindings(PracticeTestEvidence.bindings(ScmSignals.PULL_REQUEST_OPENED));
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
                "scm.pull_request",
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
                Instant.now(),
                "LIVE"
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
                "scm.pull_request",
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
                now,
                "LIVE"
            );

            int second = observationRepository.insertIfAbsent(
                id2,
                "dup-key",
                agentJob.getId(),
                practice.getId(),
                null, // practiceRevisionId
                "scm.pull_request",
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
                now,
                "LIVE"
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
                "scm.pull_request",
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
                Instant.now(),
                "LIVE"
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
                "scm.pull_request",
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
                Instant.now(),
                "LIVE"
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
            practiceB.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
            practiceB.setWorkspace(workspaceB);
            practiceB.setSlug("practice-b");
            practiceB.setName("Practice B");
            practiceB.setCriteria("Workspace B practice");
            practiceB.setBindings(PracticeTestEvidence.bindings(ScmSignals.PULL_REQUEST_OPENED));
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
                "scm.pull_request",
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
                Instant.now(),
                "LIVE"
            );
            // Finding in workspace B
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                "ws-b-key",
                agentJobB.getId(),
                practiceB.getId(),
                null, // practiceRevisionId
                "scm.pull_request",
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
                Instant.now(),
                "LIVE"
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
    class PracticeRemovalTests {

        /**
         * Removing a practice from the catalog is refused while anything was ever measured against it.
         *
         * <p>A cascading foreign key here would let pruning the catalog silently erase the recorded
         * history of everyone measured against that practice — the substrate the whole product exists to
         * build. Practices retire; measurements persist, and the database is what enforces it.
         */
        @Test
        void refusesToRemoveAPracticeThatHasBeenMeasuredAgainst() {
            // Create a second practice with its own finding
            Practice otherPractice = new Practice();
            otherPractice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
            otherPractice.setWorkspace(workspace);
            otherPractice.setSlug("other-practice");
            otherPractice.setName("Other Practice");
            otherPractice.setCriteria("Other description");
            otherPractice.setBindings(PracticeTestEvidence.bindings(ScmSignals.PULL_REQUEST_OPENED));
            otherPractice = practiceRepository.save(otherPractice);

            // Finding on the practice to be deleted
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                "cascade-key-1",
                agentJob.getId(),
                practice.getId(),
                null, // practiceRevisionId
                "scm.pull_request",
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
                Instant.now(),
                "LIVE"
            );
            // Finding on the other practice (should survive)
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                "cascade-key-2",
                agentJob.getId(),
                otherPractice.getId(),
                null, // practiceRevisionId
                "scm.pull_request",
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
                Instant.now(),
                "LIVE"
            );
            assertThat(observationRepository.findAll()).hasSize(2);

            Long measuredPracticeId = practice.getId();
            assertThatThrownBy(() -> {
                practiceRepository.deleteById(measuredPracticeId);
                practiceRepository.flush();
            }).isInstanceOf(DataIntegrityViolationException.class);

            assertThat(observationRepository.findAll())
                .extracting(Observation::getOccurrenceKey)
                .containsExactlyInAnyOrder("cascade-key-1", "cascade-key-2");
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
                "scm.pull_request",
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
                observedAt,
                "LIVE"
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
    class ArtifactKindTests {

        @Test
        @DisplayName("persisted 'PULL_REQUEST' maps to ArtifactKinds.PULL_REQUEST on read")
        void enumRoundTrip() {
            UUID id = UUID.randomUUID();
            observationRepository.insertIfAbsent(
                id,
                "tt-roundtrip",
                agentJob.getId(),
                practice.getId(),
                null, // practiceRevisionId
                "scm.pull_request",
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
                Instant.now(),
                "LIVE"
            );

            Observation found = observationRepository.findById(id).orElseThrow();
            assertThat(found.getArtifactKind()).isEqualTo(ArtifactKinds.PULL_REQUEST);
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
            insert(key, jobId, practice, artifactId, presence, at);
        }

        private void insert(
            String key,
            UUID jobId,
            Practice targetPractice,
            long artifactId,
            String presence,
            Instant at
        ) {
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                key,
                jobId,
                targetPractice.getId(),
                null,
                "scm.pull_request",
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
                at,
                "LIVE"
            );
        }

        @Test
        @DisplayName("equal observed_at timestamps tiebreak on agent_job_id, deterministically")
        void tiebreaksEqualTimestampsByAgentJobId() {
            PracticeArea area = new PracticeArea();
            area.setWorkspace(workspace);
            area.setSlug("tie-area");
            area.setName("Tie area");
            practice.setArea(practiceAreaRepository.save(area));
            practice = practiceRepository.save(practice);

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

            List<Observation> recent = observationRepository.findRecentByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId(),
                Instant.parse("2026-01-01T00:00:00Z"),
                PageRequest.of(0, 10)
            );
            assertThat(recent).extracting(Observation::getPresence).containsExactly(Presence.PRESENT);

            List<SeverityCount> severities = observationRepository.countBySeverityForDeveloper(
                aboutUser.getId(),
                workspace.getId(),
                Instant.parse("2026-01-01T00:00:00Z")
            );
            assertThat(severities).hasSize(1);
            assertThat(severities.get(0).getCount()).isEqualTo(1L);

            List<PresenceCount> presences = observationRepository.countByPresenceForDeveloper(
                aboutUser.getId(),
                workspace.getId(),
                Instant.parse("2026-01-01T00:00:00Z")
            );
            assertThat(presences).hasSize(1);
            assertThat(presences.get(0).getPresence()).isEqualTo(Presence.PRESENT);
            assertThat(presences.get(0).getCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("latest runs are selected within the requested workspace")
        void scopesLatestRunToWorkspace() {
            long artifactId = 42L;
            insert("workspace-a", agentJob.getId(), artifactId, "PRESENT", Instant.parse("2026-03-20T10:00:00Z"));

            Workspace otherWorkspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("finding-other"));
            Practice otherPractice = new Practice();
            otherPractice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
            otherPractice.setWorkspace(otherWorkspace);
            otherPractice.setSlug("other-practice");
            otherPractice.setName("Other Practice");
            otherPractice.setCriteria("Other criteria");
            otherPractice.setBindings(PracticeTestEvidence.bindings(ScmSignals.PULL_REQUEST_OPENED));
            otherPractice = practiceRepository.save(otherPractice);

            AgentJob otherJob = new AgentJob();
            otherJob.setWorkspace(otherWorkspace);
            otherJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            otherJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            otherJob = agentJobRepository.save(otherJob);
            insert(
                "workspace-b",
                otherJob.getId(),
                otherPractice,
                artifactId,
                "ABSENT",
                Instant.parse("2026-03-20T11:00:00Z")
            );

            List<DeveloperPracticeSummaryProjection> summary = observationRepository.findSummaryByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId()
            );

            assertThat(summary).hasSize(1);
            assertThat(summary.get(0).getGoodCount()).isEqualTo(1L);
            assertThat(summary.get(0).getBadCount()).isZero();
        }
    }

    @Nested
    class HiddenRepositoryExclusionTests {

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
        }

        private void insertBad(String key, long artifactId, Instant at) {
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                key,
                agentJob.getId(),
                practice.getId(),
                null,
                "scm.pull_request",
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
                at,
                "LIVE"
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

    /**
     * A confirmed campaign spends real money. Before this, nine {@code origin <> 'BACKFILL'} predicates kept
     * every one of its observations off every developer read surface, so it produced something nobody could
     * see. The reflective surface admits them; the per-practice summary, which is read as a trend, still does
     * not.
     */
    @Nested
    class BackfillVisibilityTests {

        private AgentJob campaignJob() {
            AgentJob job = new AgentJob();
            job.setWorkspace(workspace);
            job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            return agentJobRepository.save(job);
        }

        private void insert(String key, UUID jobId, long artifactId, Instant at, String origin) {
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                key,
                jobId,
                practice.getId(),
                null,
                "scm.pull_request",
                artifactId,
                aboutUser.getId(),
                "Backfill visibility observation",
                "ABSENT",
                "BAD",
                "MAJOR",
                0.9f,
                null,
                null,
                null,
                at,
                origin
            );
        }

        @Test
        @DisplayName("a campaign's finding on the developer's own work reaches the reflective surface")
        void backfilledObservationsAreVisibleToTheDeveloper() {
            insert("bf-only", campaignJob().getId(), 900L, Instant.parse("2026-03-20T10:00:00Z"), "BACKFILL");

            List<Observation> recent = observationRepository.findRecentByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId(),
                Instant.parse("2026-01-01T00:00:00Z"),
                PageRequest.of(0, 50)
            );

            assertThat(recent)
                .as(
                    "a backfilled BAD on the developer's own pull request is exactly what 'what should I work on' asks for"
                )
                .extracting(Observation::getArtifactId)
                .containsExactly(900L);
        }

        @Test
        @DisplayName("a later campaign does not erase already-delivered live feedback")
        void aCampaignDoesNotDisplaceTheLiveReading() {
            // Same artifact, campaign strictly newer. An origin-blind latest-run correlation would make the
            // campaign's job "the latest run" and drop the live row the developer was actually sent.
            insert("live-reading", agentJob.getId(), 901L, Instant.parse("2026-03-20T10:00:00Z"), "LIVE");
            insert("campaign-reading", campaignJob().getId(), 901L, Instant.parse("2026-03-21T10:00:00Z"), "BACKFILL");

            List<Observation> recent = observationRepository.findRecentByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId(),
                Instant.parse("2026-01-01T00:00:00Z"),
                PageRequest.of(0, 50)
            );

            assertThat(recent)
                .as("the latest run is selected within each origin class, so both readings survive")
                .extracting(Observation::getOrigin)
                .containsExactlyInAnyOrder(ObservationOrigin.BACKFILL, ObservationOrigin.LIVE);
        }

        @Test
        @DisplayName("the re-review multiplier is still deduped within the campaign's own origin class")
        void latestRunStillDedupesWithinTheBackfillClass() {
            insert("bf-older", campaignJob().getId(), 902L, Instant.parse("2026-03-20T10:00:00Z"), "BACKFILL");
            insert("bf-newer", campaignJob().getId(), 902L, Instant.parse("2026-03-21T10:00:00Z"), "BACKFILL");

            List<Observation> recent = observationRepository.findRecentByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId(),
                Instant.parse("2026-01-01T00:00:00Z"),
                PageRequest.of(0, 50)
            );

            assertThat(recent).hasSize(1);
            assertThat(recent.get(0).getObservedAt()).isEqualTo(Instant.parse("2026-03-21T10:00:00Z"));
        }

        @Test
        @DisplayName("the per-practice summary still excludes the campaign: a hindsight sweep is not a trend point")
        void theSummaryTrendStaysLiveOnly() {
            insert("summary-live", agentJob.getId(), 903L, Instant.parse("2026-03-20T10:00:00Z"), "LIVE");
            insert("summary-backfill", campaignJob().getId(), 904L, Instant.parse("2026-03-21T10:00:00Z"), "BACKFILL");

            List<DeveloperPracticeSummaryProjection> summary = observationRepository.findSummaryByDeveloperAndWorkspace(
                aboutUser.getId(),
                workspace.getId()
            );

            assertThat(summary).hasSize(1);
            assertThat(summary.get(0).getTotalObservations()).isEqualTo(1L);
            assertThat(summary.get(0).getLastObservedAt()).isEqualTo(Instant.parse("2026-03-20T10:00:00Z"));
        }
    }
}
