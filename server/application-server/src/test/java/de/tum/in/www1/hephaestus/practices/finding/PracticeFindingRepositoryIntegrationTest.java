package de.tum.in.www1.hephaestus.practices.finding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.job.AgentJob;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.model.Practice;
import de.tum.in.www1.hephaestus.practices.model.PracticeFinding;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.TestUserFactory;
import de.tum.in.www1.hephaestus.testconfig.WorkspaceTestFactory;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("PracticeFindingRepository Integration")
class PracticeFindingRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private PracticeFindingRepository practiceFindingRepository;

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
    private User contributor;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        workspace = workspaceRepository.save(WorkspaceTestFactory.activeWorkspace("finding-test"));

        practice = new Practice();
        practice.setWorkspace(workspace);
        practice.setSlug("test-practice");
        practice.setName("Test Practice");
        practice.setCategory("test");
        practice.setDescription("Test description");
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
        contributor = TestUserFactory.createUser(100L, "test-contributor", provider);
        contributor = userRepository.save(contributor);
    }

    @Nested
    @DisplayName("insertIfAbsent")
    class InsertIfAbsentTests {

        @Test
        @DisplayName("inserts new finding and returns 1")
        void insertsNewFinding() {
            UUID id = UUID.randomUUID();
            int result = practiceFindingRepository.insertIfAbsent(
                id,
                "key-1",
                agentJob.getId(),
                practice.getId(),
                "PULL_REQUEST",
                42L,
                contributor.getId(),
                "Good PR description",
                "POSITIVE",
                "INFO",
                0.95f,
                null,
                "Good quality",
                "Keep it up",
                "COACHING",
                Instant.now()
            );

            assertThat(result).isEqualTo(1);

            PracticeFinding found = practiceFindingRepository.findById(id).orElseThrow();
            assertThat(found.getIdempotencyKey()).isEqualTo("key-1");
            assertThat(found.getTitle()).isEqualTo("Good PR description");
            assertThat(found.getVerdict().name()).isEqualTo("POSITIVE");
            assertThat(found.getSeverity().name()).isEqualTo("INFO");
            assertThat(found.getConfidence()).isEqualTo(0.95f);
            assertThat(found.getReasoning()).isEqualTo("Good quality");
            assertThat(found.getGuidance()).isEqualTo("Keep it up");
            assertThat(found.getGuidanceMethod().name()).isEqualTo("COACHING");
        }

        @Test
        @DisplayName("returns 0 on duplicate idempotency key")
        void rejectsDuplicate() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            Instant now = Instant.now();

            int first = practiceFindingRepository.insertIfAbsent(
                id1,
                "dup-key",
                agentJob.getId(),
                practice.getId(),
                "PULL_REQUEST",
                1L,
                contributor.getId(),
                "Duplicate test",
                "POSITIVE",
                "INFO",
                0.8f,
                null,
                null,
                null,
                null,
                now
            );

            int second = practiceFindingRepository.insertIfAbsent(
                id2,
                "dup-key",
                agentJob.getId(),
                practice.getId(),
                "PULL_REQUEST",
                2L,
                contributor.getId(),
                "Should not insert",
                "NEGATIVE",
                "MAJOR",
                0.5f,
                null,
                null,
                null,
                null,
                now
            );

            assertThat(first).isEqualTo(1);
            assertThat(second).isEqualTo(0);
            assertThat(practiceFindingRepository.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("inserts finding with JSONB evidence")
        void insertsWithEvidence() {
            UUID id = UUID.randomUUID();
            String evidence = "{\"files\":[\"src/Main.java\"],\"diff_lines\":42}";

            int result = practiceFindingRepository.insertIfAbsent(
                id,
                "evidence-key",
                agentJob.getId(),
                practice.getId(),
                "PULL_REQUEST",
                99L,
                contributor.getId(),
                "Missing error handling in Main.java",
                "NEGATIVE",
                "MAJOR",
                0.7f,
                evidence,
                "Missing error handling",
                null,
                null,
                Instant.now()
            );

            assertThat(result).isEqualTo(1);

            PracticeFinding found = practiceFindingRepository.findById(id).orElseThrow();
            assertThat(found.getEvidence()).isNotNull();
            assertThat(found.getEvidence().get("files").get(0).asText()).isEqualTo("src/Main.java");
            assertThat(found.getEvidence().get("diff_lines").asInt()).isEqualTo(42);
        }
    }

    @Nested
    @DisplayName("Workspace purge")
    class WorkspacePurgeTests {

        @Test
        @DisplayName("deleteAllByPracticeWorkspaceId removes findings for workspace practices")
        void deletesFindings() {
            UUID id = UUID.randomUUID();
            practiceFindingRepository.insertIfAbsent(
                id,
                "purge-key",
                agentJob.getId(),
                practice.getId(),
                "PULL_REQUEST",
                1L,
                contributor.getId(),
                "Purge test finding",
                "POSITIVE",
                "INFO",
                0.9f,
                null,
                null,
                null,
                null,
                Instant.now()
            );
            assertThat(practiceFindingRepository.findAll()).hasSize(1);

            practiceFindingRepository.deleteAllByPracticeWorkspaceId(workspace.getId());

            assertThat(practiceFindingRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Workspace isolation")
    class WorkspaceIsolationTests {

        @Test
        @DisplayName("purging workspace A does not affect workspace B findings")
        void purgeDoesNotAffectOtherWorkspace() {
            // Create workspace B with its own practice and finding
            Workspace workspaceB = workspaceRepository.save(WorkspaceTestFactory.activeWorkspace("ws-b"));
            Practice practiceB = new Practice();
            practiceB.setWorkspace(workspaceB);
            practiceB.setSlug("practice-b");
            practiceB.setName("Practice B");
            practiceB.setCategory("test");
            practiceB.setDescription("Workspace B practice");
            practiceB.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            practiceB = practiceRepository.save(practiceB);

            AgentJob agentJobB = new AgentJob();
            agentJobB.setWorkspace(workspaceB);
            agentJobB.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            agentJobB.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            agentJobB = agentJobRepository.save(agentJobB);

            // Finding in workspace A
            practiceFindingRepository.insertIfAbsent(
                UUID.randomUUID(),
                "ws-a-key",
                agentJob.getId(),
                practice.getId(),
                "PULL_REQUEST",
                1L,
                contributor.getId(),
                "WS-A finding",
                "POSITIVE",
                "INFO",
                0.9f,
                null,
                null,
                null,
                null,
                Instant.now()
            );
            // Finding in workspace B
            practiceFindingRepository.insertIfAbsent(
                UUID.randomUUID(),
                "ws-b-key",
                agentJobB.getId(),
                practiceB.getId(),
                "PULL_REQUEST",
                2L,
                contributor.getId(),
                "WS-B finding",
                "NEGATIVE",
                "MINOR",
                0.5f,
                null,
                null,
                null,
                null,
                Instant.now()
            );
            assertThat(practiceFindingRepository.findAll()).hasSize(2);

            // Purge workspace A only
            practiceFindingRepository.deleteAllByPracticeWorkspaceId(workspace.getId());

            // Workspace B's finding must survive
            List<PracticeFinding> remaining = practiceFindingRepository.findAll();
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getIdempotencyKey()).isEqualTo("ws-b-key");
        }
    }

    @Nested
    @DisplayName("CASCADE delete")
    class CascadeDeleteTests {

        @Test
        @DisplayName("deleting a practice cascades only to its own findings")
        void cascadesFromPracticeSelectively() {
            // Create a second practice with its own finding
            Practice otherPractice = new Practice();
            otherPractice.setWorkspace(workspace);
            otherPractice.setSlug("other-practice");
            otherPractice.setName("Other Practice");
            otherPractice.setCategory("test");
            otherPractice.setDescription("Other description");
            otherPractice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            otherPractice = practiceRepository.save(otherPractice);

            // Finding on the practice to be deleted
            practiceFindingRepository.insertIfAbsent(
                UUID.randomUUID(),
                "cascade-key-1",
                agentJob.getId(),
                practice.getId(),
                "PULL_REQUEST",
                1L,
                contributor.getId(),
                "Cascade test 1",
                "NEGATIVE",
                "MAJOR",
                0.6f,
                null,
                null,
                null,
                null,
                Instant.now()
            );
            // Finding on the other practice (should survive)
            practiceFindingRepository.insertIfAbsent(
                UUID.randomUUID(),
                "cascade-key-2",
                agentJob.getId(),
                otherPractice.getId(),
                "PULL_REQUEST",
                2L,
                contributor.getId(),
                "Cascade test 2",
                "POSITIVE",
                "INFO",
                0.9f,
                null,
                null,
                null,
                null,
                Instant.now()
            );
            assertThat(practiceFindingRepository.findAll()).hasSize(2);

            practiceRepository.deleteById(practice.getId());
            practiceRepository.flush();

            // Only the finding for the deleted practice should be gone
            List<PracticeFinding> remaining = practiceFindingRepository.findAll();
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getIdempotencyKey()).isEqualTo("cascade-key-2");
        }
    }

    @Nested
    @DisplayName("findContributorPracticeSummary")
    class FindContributorPracticeSummaryTests {

        @Test
        @DisplayName("returns empty list for contributor with no findings")
        void returnsEmptyForNoFindings() {
            List<ContributorPracticeSummary> result = practiceFindingRepository.findContributorPracticeSummary(
                contributor.getId(),
                workspace.getId()
            );

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("aggregates verdict counts for a single practice")
        void aggregatesSinglePractice() {
            // Insert 3 NEGATIVE and 1 POSITIVE for the same practice
            insertFinding("sum-1", practice, "NEGATIVE", Instant.parse("2026-03-18T10:00:00Z"));
            insertFinding("sum-2", practice, "NEGATIVE", Instant.parse("2026-03-19T10:00:00Z"));
            insertFinding("sum-3", practice, "NEGATIVE", Instant.parse("2026-03-20T14:30:00Z"));
            insertFinding("sum-4", practice, "POSITIVE", Instant.parse("2026-03-17T08:00:00Z"));

            List<ContributorPracticeSummary> result = practiceFindingRepository.findContributorPracticeSummary(
                contributor.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(2); // One row per (slug, verdict) combination

            ContributorPracticeSummary negative = result
                .stream()
                .filter(s -> s.getVerdict() == Verdict.NEGATIVE)
                .findFirst()
                .orElseThrow();
            assertThat(negative.getPracticeSlug()).isEqualTo("test-practice");
            assertThat(negative.getCount()).isEqualTo(3);
            assertThat(negative.getLastDetectedAt()).isEqualTo(Instant.parse("2026-03-20T14:30:00Z"));

            ContributorPracticeSummary positive = result
                .stream()
                .filter(s -> s.getVerdict() == Verdict.POSITIVE)
                .findFirst()
                .orElseThrow();
            assertThat(positive.getCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("groups by practice slug across multiple practices")
        void groupsByPracticeSlug() {
            Practice secondPractice = new Practice();
            secondPractice.setWorkspace(workspace);
            secondPractice.setSlug("error-handling");
            secondPractice.setName("Error Handling");
            secondPractice.setCategory("test");
            secondPractice.setDescription("Handle errors");
            secondPractice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            secondPractice = practiceRepository.save(secondPractice);

            insertFinding("multi-1", practice, "POSITIVE", Instant.parse("2026-03-20T10:00:00Z"));
            insertFinding("multi-2", secondPractice, "NEGATIVE", Instant.parse("2026-03-19T10:00:00Z"));

            List<ContributorPracticeSummary> result = practiceFindingRepository.findContributorPracticeSummary(
                contributor.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(2);
            assertThat(result)
                .extracting(ContributorPracticeSummary::getPracticeSlug)
                .containsExactlyInAnyOrder("test-practice", "error-handling");
        }

        @Test
        @DisplayName("workspace isolation: excludes findings from other workspaces")
        void workspaceIsolation() {
            Workspace otherWorkspace = workspaceRepository.save(WorkspaceTestFactory.activeWorkspace("other-ws"));
            Practice otherPractice = new Practice();
            otherPractice.setWorkspace(otherWorkspace);
            otherPractice.setSlug("test-practice"); // Same slug, different workspace
            otherPractice.setName("Test Practice");
            otherPractice.setCategory("test");
            otherPractice.setDescription("Other workspace practice");
            otherPractice.setTriggerEvents(OBJECT_MAPPER.valueToTree(List.of("PullRequestCreated")));
            otherPractice = practiceRepository.save(otherPractice);

            AgentJob otherJob = new AgentJob();
            otherJob.setWorkspace(otherWorkspace);
            otherJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            otherJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            otherJob = agentJobRepository.save(otherJob);

            // Finding in target workspace
            insertFinding("iso-1", practice, "NEGATIVE", Instant.parse("2026-03-20T10:00:00Z"));
            // Finding in other workspace (same contributor)
            practiceFindingRepository.insertIfAbsent(
                UUID.randomUUID(),
                "iso-2",
                otherJob.getId(),
                otherPractice.getId(),
                "PULL_REQUEST",
                2L,
                contributor.getId(),
                "Other WS finding",
                "NEGATIVE",
                "MAJOR",
                0.8f,
                null,
                null,
                null,
                null,
                Instant.parse("2026-03-20T10:00:00Z")
            );

            List<ContributorPracticeSummary> result = practiceFindingRepository.findContributorPracticeSummary(
                contributor.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getPracticeSlug()).isEqualTo("test-practice");
            assertThat(result.get(0).getCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns correct MAX(detectedAt) as lastDetectedAt")
        void correctLastDetectedAt() {
            Instant earliest = Instant.parse("2026-03-15T08:00:00Z");
            Instant latest = Instant.parse("2026-03-20T14:30:00Z");
            Instant middle = Instant.parse("2026-03-18T12:00:00Z");

            insertFinding("time-1", practice, "NEGATIVE", earliest);
            insertFinding("time-2", practice, "NEGATIVE", latest);
            insertFinding("time-3", practice, "NEGATIVE", middle);

            List<ContributorPracticeSummary> result = practiceFindingRepository.findContributorPracticeSummary(
                contributor.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getLastDetectedAt()).isEqualTo(latest);
        }

        @Test
        @DisplayName("contributor isolation: excludes findings from other contributors")
        void contributorIsolation() {
            GitProvider provider = gitProviderRepository
                .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
                .orElseThrow();
            User otherContributor = TestUserFactory.createUser(200L, "other-contributor", provider);
            otherContributor = userRepository.save(otherContributor);

            // Finding for target contributor
            insertFinding("contrib-iso-1", practice, "NEGATIVE", Instant.parse("2026-03-20T10:00:00Z"));
            // Finding for other contributor (same practice, same workspace)
            practiceFindingRepository.insertIfAbsent(
                UUID.randomUUID(),
                "contrib-iso-2",
                agentJob.getId(),
                practice.getId(),
                "PULL_REQUEST",
                2L,
                otherContributor.getId(),
                "Other contributor finding",
                "NEGATIVE",
                "MAJOR",
                0.8f,
                null,
                null,
                null,
                null,
                Instant.parse("2026-03-20T10:00:00Z")
            );

            List<ContributorPracticeSummary> result = practiceFindingRepository.findContributorPracticeSummary(
                contributor.getId(),
                workspace.getId()
            );

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("includes NOT_APPLICABLE verdict rows in query results")
        void includesNotApplicableVerdict() {
            insertFinding("na-1", practice, "NOT_APPLICABLE", Instant.parse("2026-03-20T10:00:00Z"));
            insertFinding("na-2", practice, "NEGATIVE", Instant.parse("2026-03-19T10:00:00Z"));

            List<ContributorPracticeSummary> result = practiceFindingRepository.findContributorPracticeSummary(
                contributor.getId(),
                workspace.getId()
            );

            // Query returns all verdicts — filtering is the caller's responsibility
            assertThat(result).hasSize(2);
            assertThat(result)
                .extracting(ContributorPracticeSummary::getVerdict)
                .containsExactlyInAnyOrder(Verdict.NOT_APPLICABLE, Verdict.NEGATIVE);
        }

        /** Helper to insert a finding with minimal boilerplate. */
        private void insertFinding(String idempotencyKey, Practice targetPractice, String verdict, Instant detectedAt) {
            practiceFindingRepository.insertIfAbsent(
                UUID.randomUUID(),
                idempotencyKey,
                agentJob.getId(),
                targetPractice.getId(),
                "PULL_REQUEST",
                1L,
                contributor.getId(),
                "Test finding",
                verdict,
                "INFO",
                0.9f,
                null,
                null,
                null,
                null,
                detectedAt
            );
        }
    }
}
