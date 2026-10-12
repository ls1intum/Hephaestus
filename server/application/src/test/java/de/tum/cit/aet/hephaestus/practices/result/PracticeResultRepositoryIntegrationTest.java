package de.tum.cit.aet.hephaestus.practices.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.handler.ResultOrder;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.Team;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.TeamRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeGroupRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeGroup;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResultInvalidation;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.model.ResultOrigin;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.dto.DeveloperPracticeSummaryProjection;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceTeamRepositorySettings;
import de.tum.cit.aet.hephaestus.workspace.settings.WorkspaceTeamRepositorySettingsRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PracticeResultRepositoryIntegrationTest extends BaseIntegrationTest {

    private static final List<String> VERDICTS = List.of("MET", "NOT_MET");

    @Autowired
    private EntityManager entityManager;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private PracticeResultRepository practiceResultRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeRevisionRepository practiceRevisionRepository;

    @Autowired
    private PracticeGroupRepository practiceGroupRepository;

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

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private PracticeResultInvalidationRepository invalidationRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Workspace workspace;
    private Practice practice;
    private AgentJob agentJob;
    private User aboutUser;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();

        workspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("practice-result-test"));

        practice = new Practice();
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        practice.setWorkspace(workspace);
        practice.setSlug("test-practice");
        practice.setName("Test Practice");
        practice.setCriteria("Test description");
        PracticeTestEvidence.configure(practice, ScmSignals.PULL_REQUEST_OPENED);
        practice = practiceRepository.save(practice);
        practice = pinCurrentRevision(practice);

        agentJob = new AgentJob();
        agentJob.setWorkspace(workspace);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob = agentJobRepository.save(agentJob);

        IdentityProvider provider = gitProviderRepository
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseGet(() -> gitProviderRepository.save(
                        new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com")));
        aboutUser = TestUserFactory.createUser(100L, "test-about-user", provider);
        aboutUser = userRepository.save(aboutUser);
    }

    private Practice pinCurrentRevision(Practice practice) {
        PracticeRevision revision = practiceRevisionRepository.save(new PracticeRevision(practice, 1));
        practice.setCurrentRevision(revision);
        return practiceRepository.save(practice);
    }

    @ParameterizedTest
    @CsvSource({"v4,true", "null,true", "stale,true", "v4,false", "null,false", "stale,false"})
    void summaryUsesCurrentLatestClaimsWithoutRestoringOlderClaims(String scheme, boolean latestIsHistorical) {
        PracticeRevision current = Objects.requireNonNull(practice.getCurrentRevision());
        PracticeRevision historical = new PracticeRevision(practice, 2);
        ReflectionTestUtils.setField(
                historical,
                "reviewRuleFingerprint",
                scheme.equals("null") ? null : (scheme.equals("v4") ? "v4:" : "v5:") + "b".repeat(64));
        historical = practiceRevisionRepository.save(historical);
        Instant first = Instant.parse("2026-03-18T10:00:00Z");
        insertSummaryResult(agentJob, latestIsHistorical ? current : historical, first);
        AgentJob later = new AgentJob();
        later.setWorkspace(workspace);
        later.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        later.setConfigSnapshot(OBJECT_MAPPER.createObjectNode());
        later = agentJobRepository.save(later);
        UUID latest = insertSummaryResult(later, latestIsHistorical ? historical : current, first.plusSeconds(1));

        var summary = practiceResultRepository.findSummaryByDeveloperAndWorkspace(aboutUser.getId(), workspace.getId());
        assertThat(summary).hasSize(latestIsHistorical ? 0 : 1);
        if (!latestIsHistorical) assertThat(summary.getFirst().getMet()).isEqualTo(1L);
        invalidationRepository.save(new PracticeResultInvalidation(
                practiceResultRepository.findById(latest).orElseThrow(), 1L, "Wrong claim", first.plusSeconds(2)));
        var restored =
                practiceResultRepository.findSummaryByDeveloperAndWorkspace(aboutUser.getId(), workspace.getId());
        assertThat(restored).hasSize(latestIsHistorical ? 1 : 0);
        if (latestIsHistorical) assertThat(restored.getFirst().getMet()).isEqualTo(1L);
    }

    private UUID insertSummaryResult(AgentJob job, PracticeRevision revision, Instant at) {
        UUID id = UUID.randomUUID();
        practiceResultRepository.insertIfAbsent(
                id,
                "summary-" + id,
                job.getId(),
                workspace.getId(),
                practice.getId(),
                revision.getId(),
                "scm.pull_request",
                42L,
                aboutUser.getId(),
                "The criteria are met",
                "MET",
                null,
                null,
                null,
                "test-scope",
                null,
                null,
                null,
                at,
                "LIVE");
        return id;
    }

    @Test
    void shouldAdvanceAgainAfterReturningToAnEarlierIssueSnapshot() {
        Issue issue = persistIssue();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID returned = UUID.randomUUID();

        assertThat(issueRepository.advanceReviewSnapshot(issue.getId(), first, "snapshot-a"))
                .isOne();
        assertThat(issueRepository.advanceReviewSnapshot(issue.getId(), UUID.randomUUID(), "snapshot-a"))
                .isZero();
        assertThat(issueRepository.advanceReviewSnapshot(issue.getId(), second, "snapshot-b"))
                .isOne();
        assertThat(issueRepository.advanceReviewSnapshot(issue.getId(), returned, "snapshot-a"))
                .isOne();
        entityManager.clear();
        assertThat(issueRepository.findById(issue.getId()).orElseThrow().getReviewSnapshotId())
                .isEqualTo(returned);
    }

    @Test
    @Transactional
    void shouldKeepAdvancedSnapshotWhenManagedIssueFlushesLater() {
        Issue issue = persistIssue();
        entityManager.flush();
        UUID snapshotId = UUID.randomUUID();

        assertThat(issueRepository.advanceReviewSnapshot(issue.getId(), snapshotId, "advanced"))
                .isOne();
        issue.setTitle("Edited after snapshot advance");
        entityManager.flush();
        entityManager.clear();

        Issue reloaded = issueRepository.findById(issue.getId()).orElseThrow();
        assertThat(reloaded.getReviewSnapshotId()).isEqualTo(snapshotId);
        assertThat(reloaded.getReviewSnapshotDigest()).isEqualTo("advanced");
    }

    @Test
    void shouldSupersedeASharedIssueInEveryWorkspaceButLeaveOtherWorkCurrent() {
        Issue issue = persistIssue();
        Issue unrelated = persistIssue();
        Workspace other = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("other-issue-workspace"));
        Practice otherPractice = new Practice();
        otherPractice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        otherPractice.setWorkspace(other);
        otherPractice.setSlug("other-issue-practice");
        otherPractice.setName("Other issue practice");
        otherPractice.setCriteria("Other criterion");
        PracticeTestEvidence.configure(otherPractice, ScmSignals.PULL_REQUEST_OPENED);
        otherPractice = practiceRepository.save(otherPractice);
        AgentJob otherJob = new AgentJob();
        otherJob.setWorkspace(other);
        otherJob.setJobType(AgentJobType.ISSUE_REVIEW);
        otherJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        otherJob = agentJobRepository.save(otherJob);
        UUID currentId = insertIssueResult(issue.getId(), workspace.getId(), practice.getId(), agentJob.getId());
        UUID otherId = insertIssueResult(issue.getId(), other.getId(), otherPractice.getId(), otherJob.getId());
        UUID unrelatedId = insertIssueResult(unrelated.getId(), other.getId(), otherPractice.getId(), otherJob.getId());

        assertThat(practiceResultRepository.supersedeIssueResults(issue.getId(), Instant.now()))
                .isEqualTo(2);

        assertThat(practiceResultRepository.findById(currentId).orElseThrow().getSupersededAt())
                .isNotNull();
        assertThat(practiceResultRepository.findById(otherId).orElseThrow().getSupersededAt())
                .isNotNull();
        assertThat(practiceResultRepository.findById(unrelatedId).orElseThrow().getSupersededAt())
                .isNull();
        assertThat(practiceResultRepository.findRecentByDeveloperAndWorkspace(
                        aboutUser.getId(), workspace.getId(), Instant.EPOCH, VERDICTS, PageRequest.of(0, 10)))
                .isEmpty();
        assertThat(practiceResultRepository.findSummaryByDeveloperAndWorkspace(aboutUser.getId(), workspace.getId()))
                .isEmpty();
    }

    /**
     * The developer's recent list and the in-memory window read a claim's latest run the same way: when the
     * newest run's row was superseded, both answer with the newest run that still stands.
     *
     * <p>The writer normally prevents this state, since superseding a piece of work supersedes every run on it;
     * the older run is inserted after the supersession only to pin that the SQL rule and {@link LatestRun}
     * agree on it.
     */
    @Test
    @Transactional
    void shouldAnswerWithTheNewestStandingRunWhenTheNewestRunWasSuperseded() {
        Issue issue = persistIssue();
        AgentJob newerRun = new AgentJob();
        newerRun.setWorkspace(workspace);
        newerRun.setJobType(AgentJobType.ISSUE_REVIEW);
        newerRun.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        newerRun = agentJobRepository.save(newerRun);
        Instant newerAt = Instant.parse("2026-03-20T11:00:00Z");
        insertIssueResult(issue.getId(), workspace.getId(), practice.getId(), newerRun.getId(), newerAt);
        practiceResultRepository.supersedeIssueResults(issue.getId(), newerAt.plusSeconds(1));
        UUID standing = insertIssueResult(
                issue.getId(), workspace.getId(), practice.getId(), agentJob.getId(), newerAt.minusSeconds(3600));

        List<PracticeResult> recent = practiceResultRepository.findRecentByDeveloperAndWorkspace(
                aboutUser.getId(), workspace.getId(), Instant.EPOCH, VERDICTS, PageRequest.of(0, 10));
        List<PracticeResult> window = LatestRun.perClaim(practiceResultRepository.findByDeveloperAndWorkspaceBetween(
                aboutUser.getId(), workspace.getId(), Instant.EPOCH, newerAt.plusSeconds(60)));

        assertThat(recent).extracting(PracticeResult::getId).containsExactly(standing);
        assertThat(window).extracting(PracticeResult::getId).containsExactly(standing);
        assertThat(practiceResultRepository.findSummaryByDeveloperAndWorkspace(aboutUser.getId(), workspace.getId()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getMet()).isEqualTo(1L);
                    assertThat(row.getLastObservedAt()).isEqualTo(newerAt.minusSeconds(3600));
                });
        assertThat(practiceResultRepository.findEarlierRunsByDeveloperAndWorkspace(
                        aboutUser.getId(), workspace.getId(), Instant.EPOCH, PageRequest.of(0, 10)))
                .isEmpty();
    }

    /**
     * A run is named by the work its newest result is on, kind and id from that one row: here the older
     * row's kind and the newer row's id each sort first, so a per-column pick would pair an issue kind with a
     * pull request's id.
     */
    @Test
    void shouldNameTheNewestResultsWorkWhenARunReviewedTwoPiecesOfWork() {
        Instant olderAt = Instant.parse("2026-03-20T10:00:00Z");
        insertIssueResult(900L, workspace.getId(), practice.getId(), agentJob.getId(), olderAt);
        practiceResultRepository.insertIfAbsent(
                UUID.randomUUID(),
                "pull-request-newer",
                agentJob.getId(),
                workspace.getId(),
                practice.getId(),
                null,
                "scm.pull_request",
                100L,
                aboutUser.getId(),
                "Pull request result",
                "MET",
                null,
                null,
                null,
                "test-scope",
                null,
                null,
                null,
                olderAt.plusSeconds(60),
                "LIVE");

        List<PracticeResultRepository.DeveloperReviewRunRow> runs = practiceResultRepository
                .findDeveloperReviewRuns(aboutUser.getId(), workspace.getId(), null, null, null, PageRequest.of(0, 50))
                .getContent();

        assertThat(runs)
                .filteredOn(run -> run.getJobId().equals(agentJob.getId()))
                .singleElement()
                .satisfies(run -> {
                    assertThat(run.getArtifactKind()).isEqualTo("scm.pull_request");
                    assertThat(run.getArtifactId()).isEqualTo(100L);
                });
    }

    private UUID insertIssueResult(long issueId, long workspaceId, long practiceId, UUID jobId) {
        return insertIssueResult(issueId, workspaceId, practiceId, jobId, Instant.now());
    }

    private UUID insertIssueResult(long issueId, long workspaceId, long practiceId, UUID jobId, Instant observedAt) {
        UUID id = UUID.randomUUID();
        assertThat(practiceResultRepository.insertIfAbsent(
                        id,
                        "issue-" + id,
                        jobId,
                        workspaceId,
                        practiceId,
                        practiceRepository
                                .findById(practiceId)
                                .map(Practice::getCurrentRevision)
                                .map(PracticeRevision::getId)
                                .orElse(null),
                        "scm.issue",
                        issueId,
                        aboutUser.getId(),
                        "Issue result",
                        "MET",
                        null,
                        null,
                        null,
                        "test-scope",
                        null,
                        null,
                        null,
                        observedAt,
                        "LIVE"))
                .isOne();
        return id;
    }

    private Issue persistIssue() {
        Repository repository = new Repository();
        repository.setNativeId(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
        repository.setProvider(aboutUser.getProvider());
        repository.setName("issue-repo");
        repository.setNameWithOwner("owner/issue-repo-" + UUID.randomUUID());
        repository.setHtmlUrl("https://github.com/" + repository.getNameWithOwner());
        repository.setDefaultBranch("main");
        repository.setCreatedAt(Instant.now());
        repository.setUpdatedAt(Instant.now());
        repository.setPushedAt(Instant.now());
        repository = repositoryRepository.save(repository);
        Issue issue = new Issue();
        issue.setNativeId(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
        issue.setProvider(aboutUser.getProvider());
        issue.setRepository(repository);
        issue.setNumber(1);
        issue.setTitle("Issue under review");
        issue.setState(Issue.State.OPEN);
        issue.setCreatedAt(Instant.now());
        issue.setUpdatedAt(Instant.now());
        return issueRepository.save(issue);
    }

    @Test
    void shouldRequireBothDeveloperAndWorkspaceWhenReadingResultDetail() {
        UUID id = UUID.randomUUID();
        practiceResultRepository.insertIfAbsent(
                id,
                "scoped-detail",
                agentJob.getId(),
                workspace.getId(),
                practice.getId(),
                practice.getCurrentRevision().getId(),
                "scm.pull_request",
                42L,
                aboutUser.getId(),
                "Review result",
                "NOT_MET",
                "MAJOR",
                null,
                null,
                "test-scope",
                null,
                null,
                null,
                Instant.now(),
                "LIVE");
        User otherDeveloper =
                userRepository.save(TestUserFactory.createUser(101L, "other-developer", aboutUser.getProvider()));
        Workspace otherWorkspace = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("other-workspace"));

        assertThat(practiceResultRepository.findByIdAndDeveloperAndWorkspace(id, aboutUser.getId(), workspace.getId()))
                .map(PracticeResult::getId)
                .contains(id);
        assertThat(practiceResultRepository.findByIdAndDeveloperAndWorkspace(
                        id, otherDeveloper.getId(), workspace.getId()))
                .isEmpty();
        assertThat(practiceResultRepository.findByIdAndDeveloperAndWorkspace(
                        id, aboutUser.getId(), otherWorkspace.getId()))
                .isEmpty();
    }

    @Nested
    class InsertIfAbsentTests {

        @Test
        void insertsNewResult() {
            UUID id = UUID.randomUUID();
            int result = practiceResultRepository.insertIfAbsent(
                    id,
                    "key-1",
                    agentJob.getId(),
                    agentJob.getWorkspace().getId(),
                    practice.getId(),
                    Objects.requireNonNull(practice.getCurrentRevision()).getId(), // practiceRevisionId
                    "scm.pull_request",
                    42L,
                    aboutUser.getId(),
                    "Good PR description",
                    "MET",
                    null,
                    null,
                    "Good quality",
                    "test-scope",
                    null,
                    null,
                    null,
                    Instant.now(),
                    "LIVE");

            assertThat(result).isEqualTo(1);

            PracticeResult found = practiceResultRepository.findById(id).orElseThrow();
            assertThat(found.getOccurrenceKey()).isEqualTo("key-1");
            assertThat(found.getSummary()).isEqualTo("Good PR description");
            assertThat(found.getOutcome()).isEqualTo(Outcome.MET);
            assertThat(found.getSeverity()).isNull();
            assertThat(found.getEvidenceRationale()).isEqualTo("Good quality");
        }

        @Test
        @DisplayName("returns 0 on duplicate idempotency key")
        void rejectsDuplicate() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            Instant now = Instant.now();

            int first = practiceResultRepository.insertIfAbsent(
                    id1,
                    "dup-key",
                    agentJob.getId(),
                    agentJob.getWorkspace().getId(),
                    practice.getId(),
                    Objects.requireNonNull(practice.getCurrentRevision()).getId(), // practiceRevisionId
                    "scm.pull_request",
                    1L,
                    aboutUser.getId(),
                    "Duplicate test",
                    "MET",
                    null,
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    now,
                    "LIVE");

            int second = practiceResultRepository.insertIfAbsent(
                    id2,
                    "dup-key",
                    agentJob.getId(),
                    agentJob.getWorkspace().getId(),
                    practice.getId(),
                    Objects.requireNonNull(practice.getCurrentRevision()).getId(), // practiceRevisionId
                    "scm.pull_request",
                    2L,
                    aboutUser.getId(),
                    "Should not insert",
                    "NOT_MET",
                    "MAJOR",
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    now,
                    "LIVE");

            assertThat(first).isEqualTo(1);
            assertThat(second).isEqualTo(0);
            assertThat(practiceResultRepository.findAll()).hasSize(1);
        }

        @Test
        void insertsWithEvidence() {
            UUID id = UUID.randomUUID();
            String evidence = "{\"files\":[\"src/Main.java\"],\"diff_lines\":42}";

            int result = practiceResultRepository.insertIfAbsent(
                    id,
                    "evidence-key",
                    agentJob.getId(),
                    agentJob.getWorkspace().getId(),
                    practice.getId(),
                    Objects.requireNonNull(practice.getCurrentRevision()).getId(), // practiceRevisionId
                    "scm.pull_request",
                    99L,
                    aboutUser.getId(),
                    "Missing error handling in Main.java",
                    "NOT_MET",
                    "MAJOR",
                    evidence,
                    "Missing error handling",
                    "test-scope",
                    null,
                    null,
                    null,
                    Instant.now(),
                    "LIVE");

            assertThat(result).isEqualTo(1);

            PracticeResult found = practiceResultRepository.findById(id).orElseThrow();
            assertThat(found.getOutcome()).isEqualTo(Outcome.NOT_MET);
            assertThat(found.getEvidence()).isNotNull();
            assertThat(found.getEvidence().get("files").get(0).asString()).isEqualTo("src/Main.java");
            assertThat(found.getEvidence().get("diff_lines").asInt()).isEqualTo(42);
        }

        @Test
        void shouldStoreTheScopeExampleCoverageAndProvenanceWhenInserted() {
            UUID id = UUID.randomUUID();
            String example = """
                    {"citation":{"sourceKind":"scm.pull-request.core","artifactPath":"context/pull_request.json",\
                    "path":"pull_request.json","startLine":1,"endLine":1,"quote":"## Testing"},\
                    "note":"The description says how the change was tested."}""";
            String coverage = """
                    {"source":"host","complete":true,"reads":3,"truncatedReads":0,"failedReads":0,"omitted":[]}""";
            String provenance = """
                    {"jobId":"%s","attempt":1,"program":{"kind":"BUILTIN","digest":null}}""".formatted(agentJob.getId());

            assertThat(practiceResultRepository.insertIfAbsent(
                            id,
                            "new-columns-" + id,
                            agentJob.getId(),
                            workspace.getId(),
                            practice.getId(),
                            Objects.requireNonNull(practice.getCurrentRevision())
                                    .getId(),
                            "scm.pull_request",
                            42L,
                            aboutUser.getId(),
                            "The description says how it was tested",
                            "MET",
                            null,
                            null,
                            "The Testing section names the commands",
                            "scm.pull_request:42:AUTHOR",
                            example,
                            coverage,
                            provenance,
                            Instant.now(),
                            "LIVE"))
                    .isOne();

            PracticeResult found = practiceResultRepository.findById(id).orElseThrow();
            assertThat(found.getScopeKey()).isEqualTo("scm.pull_request:42:AUTHOR");
            JsonNode storedExample = Objects.requireNonNull(found.getExample());
            assertThat(storedExample.path("note").asString())
                    .isEqualTo("The description says how the change was tested.");
            assertThat(storedExample.path("citation").path("path").asString()).isEqualTo("pull_request.json");
            JsonNode storedCoverage = Objects.requireNonNull(found.getCoverage());
            assertThat(storedCoverage.path("source").asString()).isEqualTo("host");
            assertThat(storedCoverage.path("reads").asInt()).isEqualTo(3);
            JsonNode storedProvenance = Objects.requireNonNull(found.getProvenance());
            assertThat(storedProvenance.path("jobId").asString())
                    .isEqualTo(agentJob.getId().toString());
            assertThat(storedProvenance.path("program").path("kind").asString()).isEqualTo("BUILTIN");
        }

        @Test
        void shouldLeaveExampleCoverageAndProvenanceNullWhenNoneWasGiven() {
            UUID id = UUID.randomUUID();
            practiceResultRepository.insertIfAbsent(
                    id,
                    "no-new-columns-" + id,
                    agentJob.getId(),
                    workspace.getId(),
                    practice.getId(),
                    null,
                    "scm.pull_request",
                    42L,
                    aboutUser.getId(),
                    "Nothing to judge",
                    "NOT_APPLICABLE",
                    null,
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    Instant.now(),
                    "LIVE");

            PracticeResult found = practiceResultRepository.findById(id).orElseThrow();
            assertThat(found.getScopeKey()).isEqualTo("test-scope");
            assertThat(found.getExample()).isNull();
            assertThat(found.getCoverage()).isNull();
            assertThat(found.getProvenance()).isNull();
        }
    }

    @Nested
    class ObservationTests {

        private UUID insertNotMet(String key) {
            UUID id = UUID.randomUUID();
            assertThat(practiceResultRepository.insertIfAbsent(
                            id,
                            key + "-" + id,
                            agentJob.getId(),
                            workspace.getId(),
                            practice.getId(),
                            Objects.requireNonNull(practice.getCurrentRevision())
                                    .getId(),
                            "scm.pull_request",
                            42L,
                            aboutUser.getId(),
                            "Two problems",
                            "NOT_MET",
                            "MAJOR",
                            null,
                            null,
                            "test-scope",
                            null,
                            null,
                            null,
                            Instant.now(),
                            "LIVE"))
                    .isOne();
            return id;
        }

        private static String citations(String path, int line) {
            return """
                    [{"sourceKind":"scm.pull-request.diff","artifactPath":"context/diff.patch","path":"%s",\
                    "side":"NEW","startLine":%d,"endLine":%d,"quote":"catch (Exception e) {}"}]""".formatted(path, line, line);
        }

        @Test
        void shouldReadEachProblemInDisplayOrderWhenAResultHasSeveral() {
            UUID resultId = insertNotMet("children");
            UUID second = UUID.randomUUID();
            UUID first = UUID.randomUUID();
            assertThat(observationRepository.insert(
                            second,
                            workspace.getId(),
                            resultId,
                            1,
                            null,
                            "MINOR",
                            "Swallowed error in the player",
                            "The catch block drops the error.",
                            citations("src/Player.java", 40),
                            null,
                            null,
                            null))
                    .isOne();
            assertThat(observationRepository.insert(
                            first,
                            workspace.getId(),
                            resultId,
                            0,
                            "swallowed-recorder-error",
                            "MAJOR",
                            "Swallowed error in the recorder",
                            "The catch block drops the error the recorder reports.",
                            citations("src/Recorder.java", 212),
                            "{\"type\":\"introduced\",\"head\":0,\"explanation\":\"This change adds the catch.\"}",
                            "Log the error and rethrow it.",
                            "recurrence-recorder"))
                    .isOne();

            List<Observation> problems =
                    observationRepository.findByPracticeResultIds(workspace.getId(), List.of(resultId));

            assertThat(problems).extracting(Observation::getId).containsExactly(first, second);
            Observation recorded = problems.getFirst();
            assertThat(recorded.getPracticeResultId()).isEqualTo(resultId);
            assertThat(recorded.getWorkspaceId()).isEqualTo(workspace.getId());
            assertThat(recorded.getPosition()).isZero();
            assertThat(recorded.getObservationKey()).isEqualTo("swallowed-recorder-error");
            assertThat(recorded.getSeverity()).isEqualTo(Severity.MAJOR);
            assertThat(recorded.getRecurrenceKey()).isEqualTo("recurrence-recorder");
            assertThat(recorded.getNextStep()).isEqualTo("Log the error and rethrow it.");
            assertThat(recorded.getCitations().path(0).path("path").asString()).isEqualTo("src/Recorder.java");
            assertThat(Objects.requireNonNull(recorded.getBasis()).path("type").asString())
                    .isEqualTo("introduced");
            Observation legacy = problems.get(1);
            assertThat(legacy.getBasis()).isNull();
            assertThat(legacy.getRecurrenceKey()).isNull();
            assertThat(legacy.getObservationKey()).isNull();
        }

        @Test
        void shouldFileNoProblemWhenTheResultBelongsToAnotherWorkspace() {
            UUID resultId = insertNotMet("foreign-parent");
            Workspace other = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("observation-other"));
            UUID problemId = UUID.randomUUID();

            assertThat(observationRepository.insert(
                            problemId,
                            other.getId(),
                            resultId,
                            0,
                            null,
                            "MAJOR",
                            "Swallowed error",
                            "The catch block drops the error.",
                            citations("src/Recorder.java", 212),
                            null,
                            null,
                            null))
                    .isZero();
            assertThat(observationRepository.findById(problemId)).isEmpty();
        }

        @Test
        void shouldReadNoProblemWhenAskedForAnotherWorkspace() {
            UUID resultId = insertNotMet("scoped-read");
            UUID problemId = UUID.randomUUID();
            observationRepository.insert(
                    problemId,
                    workspace.getId(),
                    resultId,
                    0,
                    null,
                    "MAJOR",
                    "Swallowed error",
                    "The catch block drops the error.",
                    citations("src/Recorder.java", 212),
                    null,
                    null,
                    null);
            Workspace other = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("observation-reader"));

            assertThat(observationRepository.findByPracticeResultIds(other.getId(), List.of(resultId)))
                    .isEmpty();
            assertThat(observationRepository.findAllByIds(other.getId(), List.of(problemId)))
                    .isEmpty();
            assertThat(observationRepository.findAllByIds(workspace.getId(), List.of(problemId)))
                    .extracting(Observation::getId)
                    .containsExactly(problemId);
        }

        @Test
        void shouldEraseTheProblemsWhenTheirResultIsPurged() {
            UUID resultId = insertNotMet("purged-parent");
            UUID problemId = UUID.randomUUID();
            observationRepository.insert(
                    problemId,
                    workspace.getId(),
                    resultId,
                    0,
                    null,
                    "MAJOR",
                    "Swallowed error",
                    "The catch block drops the error.",
                    citations("src/Recorder.java", 212),
                    null,
                    null,
                    null);

            practiceResultRepository.deleteAllByPracticeWorkspaceId(workspace.getId());

            assertThat(observationRepository.findById(problemId)).isEmpty();
        }
    }

    @Nested
    class WorkspacePurgeTests {

        @Test
        @DisplayName("deleteAllByPracticeWorkspaceId removes results for workspace practices")
        void deletesResults() {
            UUID id = UUID.randomUUID();
            practiceResultRepository.insertIfAbsent(
                    id,
                    "purge-key",
                    agentJob.getId(),
                    agentJob.getWorkspace().getId(),
                    practice.getId(),
                    Objects.requireNonNull(practice.getCurrentRevision()).getId(), // practiceRevisionId
                    "scm.pull_request",
                    1L,
                    aboutUser.getId(),
                    "Purge test result",
                    "MET",
                    null,
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    Instant.now(),
                    "LIVE");
            assertThat(practiceResultRepository.findAll()).hasSize(1);

            practiceResultRepository.deleteAllByPracticeWorkspaceId(workspace.getId());

            assertThat(practiceResultRepository.findAll()).isEmpty();
        }
    }

    @Nested
    class WorkspaceIsolationTests {

        @Test
        void purgeDoesNotAffectOtherWorkspace() {
            Workspace workspaceB = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("ws-b"));
            Practice practiceB = new Practice();
            practiceB.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
            practiceB.setWorkspace(workspaceB);
            practiceB.setSlug("practice-b");
            practiceB.setName("Practice B");
            practiceB.setCriteria("Workspace B practice");
            PracticeTestEvidence.configure(practiceB, ScmSignals.PULL_REQUEST_OPENED);
            practiceB = practiceRepository.save(practiceB);
            practiceB = pinCurrentRevision(practiceB);

            AgentJob agentJobB = new AgentJob();
            agentJobB.setWorkspace(workspaceB);
            agentJobB.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            agentJobB.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            agentJobB = agentJobRepository.save(agentJobB);

            practiceResultRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    "ws-a-key",
                    agentJob.getId(),
                    agentJob.getWorkspace().getId(),
                    practice.getId(),
                    Objects.requireNonNull(practice.getCurrentRevision()).getId(), // practiceRevisionId
                    "scm.pull_request",
                    1L,
                    aboutUser.getId(),
                    "WS-A result",
                    "MET",
                    null,
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    Instant.now(),
                    "LIVE");
            practiceResultRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    "ws-b-key",
                    agentJobB.getId(),
                    agentJobB.getWorkspace().getId(),
                    practiceB.getId(),
                    null, // practiceRevisionId
                    "scm.pull_request",
                    2L,
                    aboutUser.getId(),
                    "WS-B result",
                    "NOT_MET",
                    "MINOR",
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    Instant.now(),
                    "LIVE");
            assertThat(practiceResultRepository.findAll()).hasSize(2);

            practiceResultRepository.deleteAllByPracticeWorkspaceId(workspace.getId());

            List<PracticeResult> remaining = practiceResultRepository.findAll();
            assertThat(remaining).hasSize(1);
            assertThat(remaining.get(0).getOccurrenceKey()).isEqualTo("ws-b-key");
        }
    }

    @Nested
    class PracticeRemovalTests {

        /** Deleting a practice must not cascade to its recorded results. */
        @Test
        void refusesToRemoveAPracticeThatHasBeenMeasuredAgainst() {
            Practice otherPractice = new Practice();
            otherPractice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
            otherPractice.setWorkspace(workspace);
            otherPractice.setSlug("other-practice");
            otherPractice.setName("Other Practice");
            otherPractice.setCriteria("Other description");
            PracticeTestEvidence.configure(otherPractice, ScmSignals.PULL_REQUEST_OPENED);
            otherPractice = practiceRepository.save(otherPractice);
            otherPractice = pinCurrentRevision(otherPractice);

            practiceResultRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    "cascade-key-1",
                    agentJob.getId(),
                    agentJob.getWorkspace().getId(),
                    practice.getId(),
                    Objects.requireNonNull(practice.getCurrentRevision()).getId(), // practiceRevisionId
                    "scm.pull_request",
                    1L,
                    aboutUser.getId(),
                    "Cascade test 1",
                    "NOT_MET",
                    "MAJOR",
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    Instant.now(),
                    "LIVE");
            practiceResultRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    "cascade-key-2",
                    agentJob.getId(),
                    agentJob.getWorkspace().getId(),
                    otherPractice.getId(),
                    null, // practiceRevisionId
                    "scm.pull_request",
                    2L,
                    aboutUser.getId(),
                    "Cascade test 2",
                    "MET",
                    null,
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    Instant.now(),
                    "LIVE");
            assertThat(practiceResultRepository.findAll()).hasSize(2);

            Long measuredPracticeId = practice.getId();
            assertThatThrownBy(() -> {
                        practiceRepository.deleteById(measuredPracticeId);
                        practiceRepository.flush();
                    })
                    .isInstanceOf(DataIntegrityViolationException.class);

            assertThat(practiceResultRepository.findAll())
                    .extracting(PracticeResult::getOccurrenceKey)
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

        private void insertForJob(String key, UUID jobId, long artifactId, String outcome, Instant observedAt) {
            String severity = "NOT_MET".equals(outcome) ? "MINOR" : null;
            practiceResultRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    key,
                    jobId,
                    workspace.getId(),
                    practice.getId(),
                    Objects.requireNonNull(practice.getCurrentRevision()).getId(),
                    "scm.pull_request",
                    artifactId,
                    aboutUser.getId(),
                    "observation",
                    outcome,
                    severity,
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    observedAt,
                    "LIVE");
        }

        @Test
        @DisplayName("dashboard summary counts only the latest run per target (re-review dedup)")
        void countsOnlyLatestRunPerArtifact() {
            AgentJob laterJob = anotherJob();
            insertForJob("dedup-old", agentJob.getId(), 42L, "NOT_MET", Instant.parse("2026-03-18T10:00:00Z"));
            insertForJob("dedup-new", laterJob.getId(), 42L, "MET", Instant.parse("2026-03-20T10:00:00Z"));

            List<DeveloperPracticeSummaryProjection> result =
                    practiceResultRepository.findSummaryByDeveloperAndWorkspace(aboutUser.getId(), workspace.getId());

            assertThat(result).hasSize(1);
            DeveloperPracticeSummaryProjection row = result.get(0);
            assertThat(row.getPracticeSlug()).isEqualTo("test-practice");
            assertThat(row.getTotalResults()).isEqualTo(1L);
            assertThat(row.getMet()).isEqualTo(1L);
            assertThat(row.getNotMet()).isEqualTo(0L);
            assertThat(row.getLastObservedAt()).isEqualTo(Instant.parse("2026-03-20T10:00:00Z"));
        }

        @Test
        @DisplayName("each distinct target contributes its own latest run")
        void countsEachTargetIndependently() {
            AgentJob laterJob = anotherJob();
            insertForJob("t42-old", agentJob.getId(), 42L, "NOT_MET", Instant.parse("2026-03-18T10:00:00Z"));
            insertForJob("t42-new", laterJob.getId(), 42L, "MET", Instant.parse("2026-03-20T10:00:00Z"));
            insertForJob("t43", agentJob.getId(), 43L, "NOT_MET", Instant.parse("2026-03-19T10:00:00Z"));

            List<DeveloperPracticeSummaryProjection> result =
                    practiceResultRepository.findSummaryByDeveloperAndWorkspace(aboutUser.getId(), workspace.getId());

            assertThat(result).hasSize(1);
            DeveloperPracticeSummaryProjection row = result.get(0);
            assertThat(row.getTotalResults()).isEqualTo(2L);
            assertThat(row.getMet()).isEqualTo(1L);
            assertThat(row.getNotMet()).isEqualTo(1L);
        }

        @Test
        @DisplayName("NOT_APPLICABLE inflates totalResults but never good/bad, and is omitted from findRecent")
        void notApplicableCountedInTotalButExcludedFromRecent() {
            insertForJob("na-target", agentJob.getId(), 50L, "NOT_APPLICABLE", Instant.parse("2026-03-20T10:00:00Z"));
            insertForJob("bad-target", agentJob.getId(), 51L, "NOT_MET", Instant.parse("2026-03-20T11:00:00Z"));

            List<DeveloperPracticeSummaryProjection> summary =
                    practiceResultRepository.findSummaryByDeveloperAndWorkspace(aboutUser.getId(), workspace.getId());

            assertThat(summary).hasSize(1);
            DeveloperPracticeSummaryProjection row = summary.get(0);
            assertThat(row.getTotalResults()).isEqualTo(2L);
            assertThat(row.getMet()).isEqualTo(0L);
            assertThat(row.getNotMet()).isEqualTo(1L);

            List<PracticeResult> recent = practiceResultRepository.findRecentByDeveloperAndWorkspace(
                    aboutUser.getId(),
                    workspace.getId(),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    VERDICTS,
                    PageRequest.of(0, 50));

            assertThat(recent).hasSize(1);
            assertThat(recent.get(0).getOccurrenceKey()).isEqualTo("bad-target");
            assertThat(recent.get(0).getOutcome()).isEqualTo(Outcome.NOT_MET);
        }
    }

    @Nested
    class InvalidationTests {

        private final Instant olderAt = Instant.parse("2026-03-18T10:00:00Z");
        private final Instant newerAt = Instant.parse("2026-03-20T10:00:00Z");

        private AgentJob newerRun() {
            AgentJob job = new AgentJob();
            job.setWorkspace(workspace);
            job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            return agentJobRepository.save(job);
        }

        private UUID insertStrength(UUID jobId, String summary, Instant observedAt) {
            UUID id = UUID.randomUUID();
            practiceResultRepository.insertIfAbsent(
                    id,
                    "invalidation-" + id,
                    jobId,
                    workspace.getId(),
                    practice.getId(),
                    Objects.requireNonNull(practice.getCurrentRevision()).getId(),
                    "scm.pull_request",
                    42L,
                    aboutUser.getId(),
                    summary,
                    "MET",
                    null,
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    observedAt,
                    "LIVE");
            return id;
        }

        private PracticeResultInvalidation invalidate(UUID practiceResultId) {
            PracticeResult practiceResult = practiceResultRepository
                    .findByIdAndWorkspaceId(practiceResultId, workspace.getId())
                    .orElseThrow();
            return invalidationRepository.save(
                    new PracticeResultInvalidation(practiceResult, 1L, "Wrong when made", newerAt));
        }

        private List<UUID> recent() {
            return practiceResultRepository
                    .findRecentByDeveloperAndWorkspace(
                            aboutUser.getId(), workspace.getId(), Instant.EPOCH, VERDICTS, PageRequest.of(0, 10))
                    .stream()
                    .map(PracticeResult::getId)
                    .toList();
        }

        private List<UUID> earlier() {
            return practiceResultRepository
                    .findEarlierRunsByDeveloperAndWorkspace(
                            aboutUser.getId(), workspace.getId(), Instant.EPOCH, PageRequest.of(0, 10))
                    .stream()
                    .map(PracticeResult::getId)
                    .toList();
        }

        private List<UUID> window() {
            return LatestRun.perClaim(practiceResultRepository.findByDeveloperAndWorkspaceBetween(
                            aboutUser.getId(), workspace.getId(), Instant.EPOCH, newerAt.plusSeconds(60)))
                    .stream()
                    .map(PracticeResult::getId)
                    .toList();
        }

        /** What Practices across the workspace reads for every developer at once. */
        private List<UUID> acrossWorkspace() {
            return practiceResultRepository
                    .findLatestRunsByWorkspaceSince(workspace.getId(), List.of(aboutUser.getId()), Instant.EPOCH)
                    .stream()
                    .map(PracticeResult::getId)
                    .toList();
        }

        @Test
        void shouldLetTheEarlierValidRunSpeakWhenTheNewestRunsOnlyClaimIsInvalidated() {
            UUID older = insertStrength(agentJob.getId(), "Valid earlier claim", olderAt);
            UUID wrong = insertStrength(newerRun().getId(), "Wrong newer claim", newerAt);
            assertThat(recent()).containsExactly(wrong);
            assertThat(earlier()).containsExactly(older);

            invalidate(wrong);

            assertThat(recent()).containsExactly(older);
            assertThat(earlier()).isEmpty();
            assertThat(window()).containsExactly(older);
            assertThat(acrossWorkspace()).containsExactly(older);
            DeveloperPracticeSummaryProjection summary = practiceResultRepository
                    .findSummaryByDeveloperAndWorkspace(aboutUser.getId(), workspace.getId())
                    .getFirst();
            assertThat(summary.getTotalResults()).isEqualTo(1L);
            assertThat(summary.getLastObservedAt()).isEqualTo(olderAt);
        }

        @Test
        void shouldKeepAValidSiblingOfTheNewestRunInsteadOfFallingBack() {
            insertStrength(agentJob.getId(), "Valid earlier claim", olderAt);
            AgentJob run = newerRun();
            UUID wrong = insertStrength(run.getId(), "Wrong newer claim", newerAt);
            UUID sibling = insertStrength(run.getId(), "Valid newer claim", newerAt);

            invalidate(wrong);

            assertThat(recent()).containsExactly(sibling);
            assertThat(window()).containsExactly(sibling);
        }

        @Test
        void shouldCountTheClaimAgainOnceRestored() {
            insertStrength(agentJob.getId(), "Valid earlier claim", olderAt);
            UUID wrong = insertStrength(newerRun().getId(), "Wrong newer claim", newerAt);
            PracticeResultInvalidation invalidation = invalidate(wrong);

            invalidation.restore(1L, "Right after all", newerAt.plusSeconds(1));
            invalidationRepository.save(invalidation);

            assertThat(recent()).containsExactly(wrong);
            assertThat(window()).containsExactly(wrong);
        }

        @Test
        void shouldRefuseACorrectionNamingAnotherWorkspacesResult() {
            UUID practiceResultId = insertStrength(agentJob.getId(), "Claim", olderAt);
            Workspace other = workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("invalidation-other"));

            assertThatThrownBy(() -> jdbcTemplate.update("""
                            INSERT INTO practice_result_invalidation (id, workspace_id, practice_result_id, reason,
                                invalidated_by_account_id, invalidated_at, provider_copy)
                            VALUES (?, ?, ?, 'Wrong', 1, now(), 'PENDING')
                            """, UUID.randomUUID(), other.getId(), practiceResultId))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void shouldEraseCorrectionsWithTheResult() {
            UUID wrong = insertStrength(agentJob.getId(), "Wrong claim", olderAt);
            invalidate(wrong);

            practiceResultRepository.deleteAllByPracticeWorkspaceId(workspace.getId());

            assertThat(invalidationRepository.findHistory(workspace.getId(), wrong))
                    .isEmpty();
        }
    }

    @Nested
    class ArtifactKindTests {

        @Test
        void shouldRoundTripPullRequestArtifactKind() {
            UUID id = UUID.randomUUID();
            practiceResultRepository.insertIfAbsent(
                    id,
                    "tt-roundtrip",
                    agentJob.getId(),
                    agentJob.getWorkspace().getId(),
                    practice.getId(),
                    Objects.requireNonNull(practice.getCurrentRevision()).getId(), // practiceRevisionId
                    "scm.pull_request",
                    1L,
                    aboutUser.getId(),
                    "Artifact kind mapping",
                    "MET",
                    null,
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    Instant.now(),
                    "LIVE");

            PracticeResult found = practiceResultRepository.findById(id).orElseThrow();
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

        private void insert(String key, UUID jobId, long artifactId, String outcome, Instant at) {
            insert(key, jobId, practice, artifactId, outcome, at);
        }

        private void insert(
                String key, UUID jobId, Practice targetPractice, long artifactId, String outcome, Instant at) {
            practiceResultRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    key,
                    jobId,
                    targetPractice.getWorkspace().getId(),
                    targetPractice.getId(),
                    Objects.requireNonNull(targetPractice.getCurrentRevision()).getId(),
                    "scm.pull_request",
                    artifactId,
                    aboutUser.getId(),
                    "Tiebreak result",
                    outcome,
                    "NOT_MET".equals(outcome) ? "INFO" : null,
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    at,
                    "LIVE");
        }

        @Test
        @DisplayName("equal observed_at timestamps tiebreak on agent_job_id, deterministically")
        void tiebreaksEqualTimestampsByAgentJobId() {
            PracticeGroup group = new PracticeGroup();
            group.setWorkspace(workspace);
            group.setSlug("tie-group");
            group.setName("Tie group");
            practice.setGroup(practiceGroupRepository.save(group));
            practice = practiceRepository.save(practice);

            AgentJob jobA = anotherJob();
            AgentJob jobB = anotherJob();
            boolean jobAWins = jobA.getId().toString().compareTo(jobB.getId().toString()) > 0;
            AgentJob winner = jobAWins ? jobA : jobB;
            AgentJob loser = jobAWins ? jobB : jobA;

            Instant sameInstant = Instant.parse("2026-03-20T10:00:00Z");
            insert("tb-loser", loser.getId(), 42L, "NOT_MET", sameInstant);
            insert("tb-winner", winner.getId(), 42L, "MET", sameInstant);

            List<DeveloperPracticeSummaryProjection> summary =
                    practiceResultRepository.findSummaryByDeveloperAndWorkspace(aboutUser.getId(), workspace.getId());
            assertThat(summary).hasSize(1);
            assertThat(summary.get(0).getTotalResults()).isEqualTo(1L);
            assertThat(summary.get(0).getMet()).isEqualTo(1L);
            assertThat(summary.get(0).getNotMet()).isEqualTo(0L);

            List<PracticeResult> recent = practiceResultRepository.findRecentByDeveloperAndWorkspace(
                    aboutUser.getId(),
                    workspace.getId(),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    VERDICTS,
                    PageRequest.of(0, 10));
            assertThat(recent).extracting(PracticeResult::getOutcome).containsExactly(Outcome.MET);
            assertThat(practiceResultRepository.findEarlierRunsByDeveloperAndWorkspace(
                            aboutUser.getId(),
                            workspace.getId(),
                            Instant.parse("2026-01-01T00:00:00Z"),
                            PageRequest.of(0, 10)))
                    .extracting(PracticeResult::getOutcome)
                    .containsExactly(Outcome.NOT_MET);
        }

        @Test
        @DisplayName("latest runs are selected within the requested workspace")
        void scopesLatestRunToWorkspace() {
            long artifactId = 42L;
            insert("workspace-a", agentJob.getId(), artifactId, "MET", Instant.parse("2026-03-20T10:00:00Z"));

            Workspace otherWorkspace =
                    workspaceRepository.save(WorkspaceTestFixtures.activeWorkspace("practice-result-other"));
            Practice otherPractice = new Practice();
            otherPractice.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
            otherPractice.setWorkspace(otherWorkspace);
            otherPractice.setSlug("other-practice");
            otherPractice.setName("Other Practice");
            otherPractice.setCriteria("Other criteria");
            PracticeTestEvidence.configure(otherPractice, ScmSignals.PULL_REQUEST_OPENED);
            otherPractice = practiceRepository.save(otherPractice);
            otherPractice = pinCurrentRevision(otherPractice);

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
                    "NOT_MET",
                    Instant.parse("2026-03-20T11:00:00Z"));

            List<DeveloperPracticeSummaryProjection> summary =
                    practiceResultRepository.findSummaryByDeveloperAndWorkspace(aboutUser.getId(), workspace.getId());

            assertThat(summary).hasSize(1);
            assertThat(summary.get(0).getMet()).isEqualTo(1L);
            assertThat(summary.get(0).getNotMet()).isZero();
        }
    }

    @Nested
    class HiddenRepositoryExclusionTests {

        @Test
        @DisplayName("results on hidden-repository artifacts are excluded from all aggregate serving queries")
        void excludesHiddenRepositoryResultsOnAggregateServingQueries() {
            PracticeGroup group = new PracticeGroup();
            group.setWorkspace(workspace);
            group.setSlug("robust-error-handling");
            group.setName("Handling failure robustly");
            group = practiceGroupRepository.save(group);
            practice.setGroup(group);
            practice = practiceRepository.save(practice);

            PullRequest visiblePr = persistPullRequest("test-org/visible-repo", 201L, false);
            PullRequest hiddenPr = persistPullRequest("test-org/hidden-repo", 202L, true);
            insertBad("visible-repo-bad", visiblePr.getId(), Instant.parse("2026-03-20T10:00:00Z"));
            insertBad("hidden-repo-bad", hiddenPr.getId(), Instant.parse("2026-03-20T11:00:00Z"));

            List<DeveloperPracticeSummaryProjection> summary =
                    practiceResultRepository.findSummaryByDeveloperAndWorkspace(aboutUser.getId(), workspace.getId());
            assertThat(summary).hasSize(1);
            assertThat(summary.get(0).getTotalResults()).isEqualTo(1L);
            assertThat(summary.get(0).getLastObservedAt()).isEqualTo(Instant.parse("2026-03-20T10:00:00Z"));

            List<PracticeResult> recent = practiceResultRepository.findRecentByDeveloperAndWorkspace(
                    aboutUser.getId(),
                    workspace.getId(),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    VERDICTS,
                    PageRequest.of(0, 50));
            assertThat(recent).extracting(PracticeResult::getArtifactId).containsExactly(visiblePr.getId());
        }

        @Test
        @DisplayName("results on hidden-repository artifacts are excluded from the developer's window")
        void shouldExcludeHiddenRepositoryResultsWhenReadingTheDevelopersWindow() {
            WorkOnBothRepositories work = seedWorkOnAVisibleAndAHiddenRepository();

            List<PracticeResult> window = practiceResultRepository.findByDeveloperAndWorkspaceBetween(
                    aboutUser.getId(),
                    workspace.getId(),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-04-01T00:00:00Z"));

            assertThat(window)
                    .extracting(PracticeResult::getArtifactId)
                    .containsOnly(work.visiblePr().getId());
        }

        /**
         * A run that found nothing to judge still ran, so the run list counts it; each of the two sits under its
         * own job, which is what the hidden one has to be missing from.
         */
        @Test
        @DisplayName("runs on hidden-repository artifacts are excluded from the developer's run list")
        void shouldExcludeHiddenRepositoryRunsWhenListingTheDevelopersRuns() {
            WorkOnBothRepositories work = seedWorkOnAVisibleAndAHiddenRepository();

            List<PracticeResultRepository.DeveloperReviewRunRow> runs = practiceResultRepository
                    .findDeveloperReviewRuns(
                            aboutUser.getId(), workspace.getId(), null, null, null, PageRequest.of(0, 50))
                    .getContent();

            assertThat(runs)
                    .extracting(PracticeResultRepository.DeveloperReviewRunRow::getJobId)
                    .containsExactlyInAnyOrder(
                            agentJob.getId(), work.visibleRun().getId());
            assertThat(runs)
                    .filteredOn(run -> run.getJobId().equals(agentJob.getId()))
                    .singleElement()
                    .extracting(PracticeResultRepository.DeveloperReviewRunRow::getReviewedAt)
                    .isEqualTo(Instant.parse("2026-03-20T10:00:00Z"));
        }

        @Test
        @DisplayName("results on hidden-repository artifacts do not date when a practice was first observed")
        void shouldIgnoreHiddenRepositoryResultsWhenDatingTheFirstResult() {
            seedWorkOnAVisibleAndAHiddenRepository();

            List<PracticeResultRepository.FirstObservedRow> firstObserved =
                    practiceResultRepository.findFirstObservedAtByPractice(aboutUser.getId(), workspace.getId());

            assertThat(firstObserved).singleElement().satisfies(row -> {
                assertThat(row.getPracticeSlug()).isEqualTo(practice.getSlug());
                assertThat(row.getFirstObservedAt()).isEqualTo(Instant.parse("2026-03-19T10:00:00Z"));
            });
        }

        private record WorkOnBothRepositories(PullRequest visiblePr, AgentJob visibleRun) {}

        /**
         * A problem on each repository under this class's run, and a run with nothing to judge on each under a
         * run of its own, dated so that any hidden row that leaked would change what a query returns.
         */
        private WorkOnBothRepositories seedWorkOnAVisibleAndAHiddenRepository() {
            PullRequest visiblePr = persistPullRequest("test-org/visible-repo", 201L, false);
            PullRequest hiddenPr = persistPullRequest("test-org/hidden-repo", 202L, true);
            insertBad("visible-repo-bad", visiblePr.getId(), Instant.parse("2026-03-20T10:00:00Z"));
            insertBad("hidden-repo-bad", hiddenPr.getId(), Instant.parse("2026-03-20T11:00:00Z"));
            AgentJob visibleRun = persistAgentJob();
            AgentJob hiddenRun = persistAgentJob();
            insertNotApplicable(
                    "visible-repo-na", visibleRun.getId(), visiblePr.getId(), Instant.parse("2026-03-19T10:00:00Z"));
            insertNotApplicable(
                    "hidden-repo-na", hiddenRun.getId(), hiddenPr.getId(), Instant.parse("2026-03-18T09:00:00Z"));
            return new WorkOnBothRepositories(visiblePr, visibleRun);
        }

        private AgentJob persistAgentJob() {
            AgentJob job = new AgentJob();
            job.setWorkspace(workspace);
            job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
            job.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
            return agentJobRepository.save(job);
        }

        private void insertNotApplicable(String key, UUID jobId, long artifactId, Instant at) {
            practiceResultRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    key,
                    jobId,
                    workspace.getId(),
                    practice.getId(),
                    Objects.requireNonNull(practice.getCurrentRevision()).getId(),
                    "scm.pull_request",
                    artifactId,
                    aboutUser.getId(),
                    "Hidden-repo exclusion run with nothing to judge",
                    "NOT_APPLICABLE",
                    null,
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    at,
                    "LIVE");
        }

        private void insertBad(String key, long artifactId, Instant at) {
            practiceResultRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    key,
                    agentJob.getId(),
                    agentJob.getWorkspace().getId(),
                    practice.getId(),
                    Objects.requireNonNull(practice.getCurrentRevision()).getId(),
                    "scm.pull_request",
                    artifactId,
                    aboutUser.getId(),
                    "Hidden-repo exclusion result",
                    "NOT_MET",
                    "MAJOR",
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    at,
                    "LIVE");
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
            practiceResultRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    key,
                    jobId,
                    workspace.getId(),
                    practice.getId(),
                    Objects.requireNonNull(practice.getCurrentRevision()).getId(),
                    "scm.pull_request",
                    artifactId,
                    aboutUser.getId(),
                    "Backfill visibility result",
                    "NOT_MET",
                    "MAJOR",
                    null,
                    null,
                    "test-scope",
                    null,
                    null,
                    null,
                    at,
                    origin);
        }

        @Test
        @DisplayName("a campaign's result on the developer's own work reaches the reflective surface")
        void backfilledResultsAreVisibleToTheDeveloper() {
            insert("bf-only", campaignJob().getId(), 900L, Instant.parse("2026-03-20T10:00:00Z"), "BACKFILL");

            List<PracticeResult> recent = practiceResultRepository.findRecentByDeveloperAndWorkspace(
                    aboutUser.getId(),
                    workspace.getId(),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    VERDICTS,
                    PageRequest.of(0, 50));

            assertThat(recent).extracting(PracticeResult::getArtifactId).containsExactly(900L);
        }

        @Test
        void shouldPreserveLiveResultsWhenLaterBackfillExists() {
            insert("live-reading", agentJob.getId(), 901L, Instant.parse("2026-03-20T10:00:00Z"), "LIVE");
            insert("campaign-reading", campaignJob().getId(), 901L, Instant.parse("2026-03-21T10:00:00Z"), "BACKFILL");

            List<PracticeResult> recent = practiceResultRepository.findRecentByDeveloperAndWorkspace(
                    aboutUser.getId(),
                    workspace.getId(),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    VERDICTS,
                    PageRequest.of(0, 50));

            assertThat(recent)
                    .extracting(PracticeResult::getOrigin)
                    .containsExactlyInAnyOrder(ResultOrigin.BACKFILL, ResultOrigin.LIVE);
        }

        @Test
        @DisplayName("the re-review multiplier is still deduped within the campaign's own origin class")
        void latestRunStillDedupesWithinTheBackfillClass() {
            insert("bf-older", campaignJob().getId(), 902L, Instant.parse("2026-03-20T10:00:00Z"), "BACKFILL");
            insert("bf-newer", campaignJob().getId(), 902L, Instant.parse("2026-03-21T10:00:00Z"), "BACKFILL");

            List<PracticeResult> recent = practiceResultRepository.findRecentByDeveloperAndWorkspace(
                    aboutUser.getId(),
                    workspace.getId(),
                    Instant.parse("2026-01-01T00:00:00Z"),
                    VERDICTS,
                    PageRequest.of(0, 50));

            assertThat(recent).hasSize(1);
            assertThat(recent.get(0).getObservedAt()).isEqualTo(Instant.parse("2026-03-21T10:00:00Z"));
        }

        @Test
        @DisplayName("the per-practice summary still excludes the campaign: a hindsight sweep is not a trend point")
        void theSummaryTrendStaysLiveOnly() {
            insert("summary-live", agentJob.getId(), 903L, Instant.parse("2026-03-20T10:00:00Z"), "LIVE");
            insert("summary-backfill", campaignJob().getId(), 904L, Instant.parse("2026-03-21T10:00:00Z"), "BACKFILL");

            List<DeveloperPracticeSummaryProjection> summary =
                    practiceResultRepository.findSummaryByDeveloperAndWorkspace(aboutUser.getId(), workspace.getId());

            assertThat(summary).hasSize(1);
            assertThat(summary.get(0).getTotalResults()).isEqualTo(1L);
            assertThat(summary.get(0).getLastObservedAt()).isEqualTo(Instant.parse("2026-03-20T10:00:00Z"));
        }
    }

    @Nested
    class OperatorOrderTests {

        @Test
        void shouldRankSeveritiesLikeWorstFirstWhenPrioritizingActionable() {
            Instant base = Instant.parse("2026-03-20T10:00:00Z");
            // The less severe, the newer: newest-first alone would invert the expected order.
            List<UUID> ids = Stream.of(Severity.MINOR, Severity.CRITICAL, Severity.INFO, Severity.MAJOR)
                    .map(severity -> insertNotMet(severity, base.plusSeconds(severity.rank())))
                    .toList();
            ResultQueryFilter thisRun = new ResultQueryFilter(
                    null, null, null, null, null, null, agentJob.getId(), null, null, null, null, null, null);

            var operatorOrder = practiceResultRepository
                    .findForWorkspace(workspace.getId(), thisRun, true, PageRequest.of(0, 10))
                    .map(row -> Objects.requireNonNull(row.getSeverity()))
                    .getContent();
            var worstFirst = practiceResultRepository.findAllById(ids).stream()
                    .sorted(ResultOrder.worstFirst())
                    .map(PracticeResult::getSeverity)
                    .toList();

            assertThat(operatorOrder).containsExactly(Severity.CRITICAL, Severity.MAJOR, Severity.MINOR, Severity.INFO);
            assertThat(worstFirst).containsExactlyElementsOf(operatorOrder);
        }

        private UUID insertNotMet(Severity severity, Instant observedAt) {
            UUID id = UUID.randomUUID();
            assertThat(practiceResultRepository.insertIfAbsent(
                            id,
                            "operator-order-" + severity,
                            agentJob.getId(),
                            workspace.getId(),
                            practice.getId(),
                            null,
                            "scm.pull_request",
                            42L,
                            aboutUser.getId(),
                            "Operator order result",
                            "NOT_MET",
                            severity.name(),
                            null,
                            null,
                            "test-scope",
                            null,
                            null,
                            null,
                            observedAt,
                            "LIVE"))
                    .isOne();
            return id;
        }
    }
}
