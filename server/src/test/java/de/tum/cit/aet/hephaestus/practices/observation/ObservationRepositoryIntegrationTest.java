package de.tum.cit.aet.hephaestus.practices.observation;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeArea;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.ReviewerAudiencePractices;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.AreaStandingRow;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository.CohortStandingRow;
import de.tum.cit.aet.hephaestus.practices.observation.dto.DeveloperPracticeSummaryProjection;
import de.tum.cit.aet.hephaestus.practices.report.PracticeReportService;
import de.tum.cit.aet.hephaestus.practices.review.ReviewCycleWindowResolver;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembershipRepository;
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
    private WorkspaceMembershipRepository workspaceMembershipRepository;

    @Autowired
    private IdentityProviderRepository gitProviderRepository;

    @Autowired
    private ObservationService observationService;

    @Autowired
    private ReviewCycleWindowResolver reviewCycleWindowResolver;

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
        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspace(workspace);
        membership.setUser(aboutUser);
        membership.setRole(WorkspaceMembership.WorkspaceRole.MEMBER);
        workspaceMembershipRepository.save(membership);
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
            insertFindingForUser(key, jobId, artifactId, aboutUser.getId(), presence, at);
        }

        private void insertFindingForUser(
            String key,
            UUID jobId,
            long artifactId,
            Long aboutUserId,
            String presence,
            Instant at
        ) {
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                key,
                jobId,
                practice.getId(),
                null,
                "PULL_REQUEST",
                artifactId,
                aboutUserId,
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
        @DisplayName("R1: a reviewer's observation survives a LATER author job on the same PR (per-subject grain)")
        void reviewerObservationSurvivesLaterAuthorJobOnSamePr() {
            // Reviewer attribution (ADR 0021 C2): a REVIEWER and the AUTHOR both have observations on PR 7, and
            // the AUTHOR's job runs LATER. Latest-run dedup is per (artifact_type, artifact_id, about_user_id) —
            // so the author's later job must NOT evict the reviewer's observations from that PR. Pre-fix (grain
            // was per artifact only) the author's later run would be selected as THE latest run for the PR and
            // the reviewer's row would be excluded → the reviewer would silently vanish from every aggregate.
            GitProvider provider = gitProviderRepository
                .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
                .orElseGet(() ->
                    gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com"))
                );
            User reviewer = userRepository.save(TestUserFactory.createUser(200L, "reviewer-user", provider));

            AgentJob reviewerJob = new AgentJob();
            reviewerJob.setWorkspace(workspace);
            reviewerJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            reviewerJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            reviewerJob = agentJobRepository.save(reviewerJob);

            AgentJob authorJob = new AgentJob();
            authorJob.setWorkspace(workspace);
            authorJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            authorJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            authorJob = agentJobRepository.save(authorJob);

            insertFindingForUser(
                "r1-reviewer",
                reviewerJob.getId(),
                7L,
                reviewer.getId(),
                "ABSENT",
                Instant.parse("2026-04-01T10:00:00Z")
            );
            insertFindingForUser(
                "r1-author",
                authorJob.getId(),
                7L,
                aboutUser.getId(),
                "PRESENT",
                Instant.parse("2026-04-05T10:00:00Z") // LATER than the reviewer's run
            );

            // The reviewer's own summary still shows their PR-7 finding — not evicted by the author's later job.
            List<DeveloperPracticeSummary> reviewerSummary = observationRepository.findDeveloperPracticeSummary(
                reviewer.getId(),
                workspace.getId()
            );
            assertThat(reviewerSummary).hasSize(1);
            assertThat(reviewerSummary.get(0).getPresence()).isEqualTo(Presence.ABSENT);

            // …and the author's summary independently reflects their own latest run for the same PR.
            List<DeveloperPracticeSummary> authorSummary = observationRepository.findDeveloperPracticeSummary(
                aboutUser.getId(),
                workspace.getId()
            );
            assertThat(authorSummary).hasSize(1);
            assertThat(authorSummary.get(0).getPresence()).isEqualTo(Presence.PRESENT);
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
         * The native COUNT(DISTINCT artifact_id) / MAX(confidence) the standing floor keys on must be
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
         * caught here. The mentor standing floor keys on this distinction.
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

    /**
     * The reviewer-craft firewall at the JPQL layer (ADR 0021 C2): the author's IN_CONTEXT ledger is sourced
     * from {@code findByAgentJobIdExcludingSlugs(jobId, REVIEWER_AUDIENCE_SLUGS)}, so a reviewer-audience
     * observation persisted on the same job/PR must never appear in that source set — while the unfiltered
     * {@code findByAgentJobId} still returns it (proving the exclusion is the query's doing, not that the row
     * was never written). A regression to {@code Set.of()} would fuse a reviewer's craft into the author's
     * delivered-feedback record.
     */
    @Nested
    class ReviewerAudienceExclusionTests {

        @Test
        @DisplayName("findByAgentJobIdExcludingSlugs drops the reviewer-audience row; the unfiltered read keeps both")
        void excludesReviewerAudienceObservationsFromAuthorLedgerSource() {
            // A reviewer-audience practice (its slug IS in REVIEWER_AUDIENCE_SLUGS). The setUp `practice`
            // ("test-practice") is author-audience (NOT in the set).
            Practice reviewerPractice = new Practice();
            reviewerPractice.setWorkspace(workspace);
            reviewerPractice.setSlug("leaves-useful-specific-review-comments");
            reviewerPractice.setName("Useful review comments");
            reviewerPractice.setCriteria("Reviewer craft");
            reviewerPractice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("ReviewSubmitted")));
            reviewerPractice = practiceRepository.save(reviewerPractice);

            // Same agent job, same PR (artifact 7): one author-audience observation, one reviewer-audience.
            UUID authorObservationId = UUID.randomUUID();
            observationRepository.insertIfAbsent(
                authorObservationId,
                "excl-author-key",
                agentJob.getId(),
                practice.getId(),
                null,
                "PULL_REQUEST",
                7L,
                aboutUser.getId(),
                "Author-audience finding",
                "PRESENT",
                "GOOD",
                "INFO",
                0.9f,
                null,
                null,
                null,
                Instant.now()
            );
            UUID reviewerObservationId = UUID.randomUUID();
            observationRepository.insertIfAbsent(
                reviewerObservationId,
                "excl-reviewer-key",
                agentJob.getId(),
                reviewerPractice.getId(),
                null,
                "PULL_REQUEST",
                7L,
                aboutUser.getId(),
                "Vague review comment",
                "ABSENT",
                "BAD",
                "MINOR",
                0.9f,
                null,
                null,
                null,
                Instant.now()
            );

            // Unfiltered read: BOTH rows — so the exclusion below is the query filtering, not a missing insert.
            assertThat(observationRepository.findByAgentJobId(agentJob.getId()))
                .extracting(Observation::getId)
                .containsExactlyInAnyOrder(authorObservationId, reviewerObservationId);

            // Firewalled read: ONLY the author-audience row survives the reviewer-audience-slug exclusion.
            assertThat(
                observationRepository.findByAgentJobIdExcludingSlugs(
                    agentJob.getId(),
                    ReviewerAudiencePractices.REVIEWER_AUDIENCE_SLUGS
                )
            )
                .extracting(Observation::getId)
                .containsExactly(authorObservationId);
        }
    }

    /**
     * Quarantine parity: the cohort SQL {@code findCohortStandingByAreaAndWorkspace} and the
     * developer's own reflection ({@link ObservationService#getPracticeReport}) must reach the SAME verdict on the
     * SAME single-target BAD. Both apply the identical floor — a single-target BAD with confidence &lt; 0.5 is
     * quarantined (excluded), a confident one is not.
     *
     * <p><b>How parity is pinned.</b> Both surfaces are driven from ONE inserted observation per test (same
     * about-user, same reviewing practice, same artifact, same confidence). The recency bound is the SAME
     * value on both paths: {@code getPracticeReport} computes {@code since} internally from
     * {@link ReviewCycleWindowResolver#previousCycleWindow}, and the cohort assertion passes exactly that same
     * {@code previousCycleWindow(workspace).after()} (truncated to the minute, so both reads see it identically
     * within a test run). So any divergence between the two is a floor-application difference, not an input or
     * window difference.
     */
    @Nested
    class QuarantineParityTests {

        private Practice reviewingPractice;
        private Instant since;

        /** Seed a reviewing area + one active reviewing practice, and capture the shared recency bound. */
        private void seedReviewingArea() {
            PracticeArea area = new PracticeArea();
            area.setWorkspace(workspace);
            area.setSlug(PracticeReportService.REVIEWING_PRACTICE_AREA_SLUG);
            area.setName("Constructive code review");
            area = practiceAreaRepository.save(area);

            reviewingPractice = new Practice();
            reviewingPractice.setWorkspace(workspace);
            reviewingPractice.setArea(area);
            reviewingPractice.setSlug("leaves-useful-specific-review-comments");
            reviewingPractice.setName("Useful review comments");
            reviewingPractice.setCriteria("Reviewer craft");
            reviewingPractice.setActive(true);
            reviewingPractice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("ReviewSubmitted")));
            reviewingPractice = practiceRepository.save(reviewingPractice);

            // The exact bound getPracticeReport() will recompute internally — pins window parity across the surfaces.
            since = reviewCycleWindowResolver.previousCycleWindow(workspace).after();
        }

        private void insertBad(String key, long artifactId, float confidence) {
            observationRepository.insertIfAbsent(
                UUID.randomUUID(),
                key,
                agentJob.getId(),
                reviewingPractice.getId(),
                null,
                "PULL_REQUEST",
                artifactId,
                aboutUser.getId(),
                "Vague review comment",
                "ABSENT",
                "BAD",
                "MINOR",
                confidence,
                null,
                null, // recurrence_key null → corroboration falls back to the whole-group distinct-target count
                null,
                Instant.now()
            );
        }

        /** The cohort query's EFFECTIVE bad count for our (developer, reviewing practice) pair (0 if no row). */
        private long cohortBadCount() {
            return observationRepository
                .findCohortStandingByAreaAndWorkspace(
                    workspace.getId(),
                    PracticeReportService.REVIEWING_PRACTICE_AREA_SLUG,
                    since
                )
                .stream()
                .filter(
                    r ->
                        reviewingPractice.getSlug().equals(r.getPracticeSlug()) &&
                        aboutUser.getId().equals(r.getAboutUserId())
                )
                .mapToLong(CohortStandingRow::getBadCount)
                .sum();
        }

        /** Whether the developer's own reflection surfaces a {@code toWorkOn} item for the reviewing practice. */
        private boolean reflectionFlagsPractice() {
            return observationService
                .getPracticeReport(workspace.getId(), aboutUser.getId())
                .stream()
                .anyMatch(card -> reviewingPractice.getSlug().equals(card.slug()) && !card.toWorkOn().isEmpty());
        }

        @Test
        @DisplayName("a single-target low-confidence BAD is quarantined identically on cohort and reflection")
        void quarantinedBadIsNeitherACohortProblemNorAReflectionToWorkOnItem() {
            seedReviewingArea();
            insertBad("parity-quarantined", 7L, 0.4f); // single target + confidence < 0.5 → quarantined on BOTH

            // (i) cohort: the BAD is excluded from badCount, so the developer is NOT DEVELOPING on this practice.
            long badCount = cohortBadCount();
            assertThat(badCount).isZero();
            assertThat(PracticeStatusDeriver.derive(badCount > 0, false)).isNotEqualTo(PracticeStatus.DEVELOPING);

            // (ii) reflection: the same quarantined BAD produces no toWorkOn item on the developer's own surface.
            assertThat(reflectionFlagsPractice()).isFalse();
        }

        @Test
        @DisplayName("a high-confidence BAD is flagged identically on cohort and reflection")
        void confidentBadIsFlaggedOnBothSurfaces() {
            seedReviewingArea();
            insertBad("parity-confident", 7L, 0.8f); // single target but confidence ≥ 0.5 → NOT quarantined

            // (i) cohort: the confident BAD counts → the developer is DEVELOPING on this practice.
            long badCount = cohortBadCount();
            assertThat(badCount).isEqualTo(1L);
            assertThat(PracticeStatusDeriver.derive(badCount > 0, false)).isEqualTo(PracticeStatus.DEVELOPING);

            // (ii) reflection: the same confident BAD DOES surface as a toWorkOn item — floor applied consistently.
            assertThat(reflectionFlagsPractice()).isTrue();
        }

        @Test
        @DisplayName("hidden workspace members are excluded from the practice overview cohort query")
        void hiddenMembersAreExcludedFromCohortStanding() {
            seedReviewingArea();
            insertBad("hidden-member", 7L, 0.8f);
            WorkspaceMembership membership = workspaceMembershipRepository
                .findByWorkspace_IdAndUser_Id(workspace.getId(), aboutUser.getId())
                .orElseThrow();
            membership.setHidden(true);
            workspaceMembershipRepository.saveAndFlush(membership);

            assertThat(
                observationRepository.findCohortStandingByAreaAndWorkspace(
                    workspace.getId(),
                    PracticeReportService.REVIEWING_PRACTICE_AREA_SLUG,
                    since
                )
            ).isEmpty();
        }
    }
}
