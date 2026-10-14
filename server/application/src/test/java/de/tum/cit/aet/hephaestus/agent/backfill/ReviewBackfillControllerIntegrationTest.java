package de.tum.cit.aet.hephaestus.agent.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.agent.handler.ReviewOutputService;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactSignalRepository;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalState;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalStateReason;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceResolver;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceSettingsService;
import de.tum.cit.aet.hephaestus.workspace.dto.UpdateWorkspaceFeaturesRequestDTO;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The confirmation step, end to end: a preflight must cost nothing, and the state a run is in must be
 * the only thing that decides what can be done to it.
 */
class ReviewBackfillControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReviewBackfillRunRepository runRepository;

    @Autowired
    private AutowireCapableBeanFactory beanFactory;

    @Autowired
    private WorkspaceResolver workspaceResolver;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private RepositoryToMonitorRepository monitorRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private WorkspaceLlmConnectionRepository llmConnectionRepository;

    @Autowired
    private WorkspaceLlmModelRepository llmModelRepository;

    @Autowired
    private WorkspaceAgentBindingRepository bindingRepository;

    @Autowired
    private ArtifactSignalRepository signalRepository;

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WorkspaceSettingsService workspaceSettingsService;

    /**
     * Authenticated the way production is: a numeric JWT {@code sub}, which is what
     * {@code SecurityUtils.getCurrentAccountId()} reads (ADR 0017). {@code @WithAdminUser}'s token
     * carries a non-numeric subject, and a campaign refuses to be created without an account to
     * attribute the spend to — so a test using it would prove the refusal rather than the feature.
     */
    private static final String ADMIN_ACCOUNT_TOKEN = "mock-jwt-sub-1";

    private static final Instant TO = Instant.parse("2026-08-07T00:00:00Z");
    private static final Instant FROM = TO.minus(Duration.ofDays(30));

    private Workspace setupWorkspace(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = createWorkspace(slug, "Backfill Workspace", slug + "-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }

    private static void asAdminAccount(HttpHeaders headers) {
        headers.setBearerAuth(ADMIN_ACCOUNT_TOKEN);
    }

    private Map<String, Object> window() {
        return Map.of("artifactKind", "scm.pull_request", "fromAt", FROM.toString(), "toAt", TO.toString());
    }

    /** Asking what a backfill would cost creates a decision to make, not a campaign in progress. */
    @Test
    @WithAdminUser
    void aPreflightReturnsARunAwaitingConfirmationAndAuthorisesNothing() {
        Workspace workspace = setupWorkspace("backfill-preflight");

        webTestClient
                .post()
                .uri("/workspaces/{slug}/practices/backfill-runs", workspace.getWorkspaceSlug())
                .headers(ReviewBackfillControllerIntegrationTest::asAdminAccount)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(window())
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("AWAITING_CONFIRMATION")
                .jsonPath("$.estimatedArtifacts")
                .isEqualTo(0)
                .jsonPath("$.submittedCount")
                .isEqualTo(0)
                .jsonPath("$.confirmedByAccountId")
                .doesNotExist()
                .jsonPath("$.startedAt")
                .doesNotExist();

        assertThat(runRepository.findAll()).singleElement().satisfies(run -> {
            assertThat(run.getStatus()).isEqualTo(ReviewBackfillStatus.AWAITING_CONFIRMATION);
            assertThat(run.getConfirmedByAccountId()).isNull();
            assertThat(run.getCursorArtifactId()).isNull();
        });
    }

    @Test
    @WithAdminUser
    void confirmingRecordsWhoAuthorisedTheSpend() {
        Workspace workspace = setupWorkspace("backfill-confirm");
        String runId = preflight(workspace);

        webTestClient
                .patch()
                .uri("/workspaces/{slug}/practices/backfill-runs/{runId}/status", workspace.getWorkspaceSlug(), runId)
                .headers(ReviewBackfillControllerIntegrationTest::asAdminAccount)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("status", "RUNNING"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.status")
                .isEqualTo("RUNNING")
                .jsonPath("$.confirmedByAccountId")
                .exists()
                .jsonPath("$.startedAt")
                .exists();
    }

    @Test
    @WithAdminUser
    void aCancelledCampaignCannotBeRestarted() {
        Workspace workspace = setupWorkspace("backfill-restart");
        String runId = preflight(workspace);
        patchStatus(workspace, runId, "CANCELLED").expectStatus().isOk().expectBody(Void.class);

        patchStatus(workspace, runId, "RUNNING").expectStatus().isEqualTo(409).expectBody(Void.class);
    }

    /**
     * Two overlapping campaigns would each read the other's ledger rows as "already covered", so neither
     * would cover the scope its estimate described.
     */
    @Test
    @WithAdminUser
    void aSecondCampaignIsRefusedWhileOneIsUnderWay() {
        Workspace workspace = setupWorkspace("backfill-second");
        String runId = preflight(workspace);
        patchStatus(workspace, runId, "RUNNING").expectStatus().isOk().expectBody(Void.class);

        webTestClient
                .post()
                .uri("/workspaces/{slug}/practices/backfill-runs", workspace.getWorkspaceSlug())
                .headers(ReviewBackfillControllerIntegrationTest::asAdminAccount)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(window())
                .exchange()
                .expectStatus()
                .isEqualTo(409)
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(Void.class);
    }

    /** Told at preflight, so the admin narrows the window instead of discovering the limit later. */
    @Test
    @WithAdminUser
    void anInvertedWindowIsRefusedWithAProblemDetail() {
        Workspace workspace = setupWorkspace("backfill-window");

        webTestClient
                .post()
                .uri("/workspaces/{slug}/practices/backfill-runs", workspace.getWorkspaceSlug())
                .headers(ReviewBackfillControllerIntegrationTest::asAdminAccount)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("artifactKind", "scm.pull_request", "fromAt", TO.toString(), "toAt", FROM.toString()))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody(Void.class);
    }

    @Test
    @WithAdminUser
    void aConversationThreadCannotBeBackfilled() {
        Workspace workspace = setupWorkspace("backfill-kind");

        webTestClient
                .post()
                .uri("/workspaces/{slug}/practices/backfill-runs", workspace.getWorkspaceSlug())
                .headers(ReviewBackfillControllerIntegrationTest::asAdminAccount)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "artifactKind", "chat.conversation_thread", "fromAt", FROM.toString(), "toAt", TO.toString()))
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody(Void.class);
    }

    /** A campaign can spend a workspace's whole monthly AI budget, so a plain member cannot start one. */
    @Test
    @WithMentorUser
    void aWorkspaceMemberCanNeitherSeeNorStartACampaign() {
        Workspace workspace = setupWorkspace("backfill-member");
        User member = persistUser("mentor");
        ensureWorkspaceMembership(workspace, member, WorkspaceRole.MEMBER);
        String slug = workspace.getWorkspaceSlug();

        webTestClient
                .get()
                .uri("/workspaces/{slug}/practices/backfill-runs", slug)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);

        webTestClient
                .post()
                .uri("/workspaces/{slug}/practices/backfill-runs", slug)
                .headers(TestAuthUtils.withCurrentUser())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(window())
                .exchange()
                .expectStatus()
                .isForbidden()
                .expectBody(Void.class);
    }

    @Test
    @WithAdminUser
    void aCampaignFromAnotherWorkspaceIsNotFound() {
        Workspace first = setupWorkspace("backfill-tenant-a");
        Workspace second = setupWorkspace("backfill-tenant-b");
        String runId = preflight(first);

        webTestClient
                .get()
                .uri("/workspaces/{slug}/practices/backfill-runs/{runId}", second.getWorkspaceSlug(), runId)
                .headers(ReviewBackfillControllerIntegrationTest::asAdminAccount)
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectBody(Void.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"scm.pull_request", "scm.issue"})
    @WithAdminUser
    void shouldSubmitInCampaignWorkspaceWhenOnlyItAllowsManualReviews(String artifactKind) {
        assertCampaignAdmission(ArtifactKind.of(artifactKind), true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"scm.pull_request", "scm.issue"})
    @WithAdminUser
    void shouldKeepRefusalInCampaignWorkspaceWhenOnlyFirstMonitorAllowsManualReviews(String artifactKind) {
        assertCampaignAdmission(ArtifactKind.of(artifactKind), false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"scm.pull_request", "scm.issue"})
    @WithAdminUser
    void shouldReadCurrentManualSettingAfterCampaignWasLoaded(String artifactKind) {
        assertCampaignAdmission(ArtifactKind.of(artifactKind), true, CampaignChange.DISABLE_MANUAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"scm.pull_request", "scm.issue"})
    @WithAdminUser
    void shouldRefuseCampaignWorkspaceAfterItsMonitorWasRemoved(String artifactKind) {
        assertCampaignAdmission(ArtifactKind.of(artifactKind), true, CampaignChange.REMOVE_MONITOR);
    }

    private enum CampaignChange {
        NONE,
        DISABLE_MANUAL,
        REMOVE_MONITOR
    }

    private void assertCampaignAdmission(ArtifactKind kind, boolean campaignEnabled) {
        assertCampaignAdmission(kind, campaignEnabled, CampaignChange.NONE);
    }

    private void assertCampaignAdmission(ArtifactKind kind, boolean campaignEnabled, CampaignChange change) {
        Workspace left = setupWorkspace("backfill-admission-left");
        Workspace right = setupWorkspace("backfill-admission-right");
        User author = persistUser("testuser");
        Repository repository = new Repository();
        repository.setNativeId(9101L);
        repository.setProvider(ensureGitHubProvider());
        repository.setName("shared-repo");
        repository.setNameWithOwner("backfill-org/shared-repo");
        repository.setHtmlUrl("https://github.com/backfill-org/shared-repo");
        repository.setDefaultBranch("main");
        repository = repositoryRepository.saveAndFlush(repository);
        for (Workspace workspace : new Workspace[] {left, right}) {
            assertThat(workspace.getStatus()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE);
            connectActivitySource(workspace, ensureGitHubProvider());
            ensureWorkspaceMembership(workspace, author, WorkspaceRole.MEMBER);
            assertThat(workspaceMembershipService.isPracticeReviewEligible(workspace.getId(), author.getId()))
                    .isTrue();
            RepositoryToMonitor monitor = new RepositoryToMonitor();
            monitor.setWorkspace(workspace);
            monitor.setNameWithOwner(repository.getNameWithOwner());
            monitor.setNativeId(repository.getNativeId());
            monitorRepository.saveAndFlush(monitor);
        }

        // A is the resolver's actual first answer; monitor queries do not promise insertion order.
        Workspace first = workspaceResolver
                .resolveForRepository(repository.getNameWithOwner())
                .orElseThrow();
        assertThat(first.getId()).isIn(left.getId(), right.getId());
        Workspace campaign = first.getId().equals(left.getId()) ? right : left;
        assertThat(workspaceResolver.resolveAllForRepository(repository.getNameWithOwner()))
                .extracting(Workspace::getId)
                .containsExactlyInAnyOrder(first.getId(), campaign.getId());
        SignalName signal =
                ArtifactKinds.PULL_REQUEST.equals(kind) ? ScmSignals.PULL_REQUEST_READY : ScmSignals.ISSUE_OPENED;
        makeReviewable(first, signal, change != CampaignChange.NONE || !campaignEnabled);
        makeReviewable(campaign, signal, campaignEnabled);
        Issue work = persistReviewedWork(repository, author, kind);
        var key = work instanceof PullRequest pr
                ? ReviewBackfillSignals.keyFor(campaign.getId(), pr).orElseThrow()
                : ReviewBackfillSignals.keyFor(campaign.getId(), work).orElseThrow();
        assertThat(key.signalName()).isEqualTo(signal);

        ReviewBackfillRun run = new ReviewBackfillRun();
        run.setWorkspace(workspaceRepository.findById(campaign.getId()).orElseThrow());
        run.setArtifactKind(kind.value());
        run.setDiscoveredVia(DiscoveredVia.BACKFILL);
        run.setFromAt(FROM);
        run.setToAt(TO);
        run.setEstimatedArtifacts(1);
        Long requesterId =
                Objects.requireNonNull(persistAccount("Backfill requester").getId());
        run.setRequestedByAccountId(requesterId);
        run.setConfirmedByAccountId(requesterId);
        run.setStartedAt(Instant.now());
        run.setStatus(ReviewBackfillStatus.RUNNING);
        run = runRepository.saveAndFlush(run);
        if (change == CampaignChange.DISABLE_MANUAL) {
            assertThat(run.getWorkspace().getFeatures().getPracticeReviewManualTriggerEnabled())
                    .isTrue();
            workspaceSettingsService.updateFeatures(
                    campaign.getId(), new UpdateWorkspaceFeaturesRequestDTO(null, null, false));
            assertThat(workspaceRepository
                            .findById(campaign.getId())
                            .orElseThrow()
                            .getFeatures()
                            .getPracticeReviewManualTriggerEnabled())
                    .isFalse();
            assertThat(run.getWorkspace().getFeatures().getPracticeReviewManualTriggerEnabled())
                    .isTrue();
        } else if (change == CampaignChange.REMOVE_MONITOR) {
            var campaignMonitor = monitorRepository.findAll().stream()
                    .filter(monitor -> Objects.equals(
                            Objects.requireNonNull(monitor.getWorkspace()).getId(), campaign.getId()))
                    .filter(monitor -> monitor.getNameWithOwner().equals("backfill-org/shared-repo"))
                    .findFirst()
                    .orElseThrow();
            monitorRepository.deleteById(campaignMonitor.getId());
            assertThat(workspaceResolver.resolveAllForRepository(repository.getNameWithOwner()))
                    .extracting(Workspace::getId)
                    .containsExactly(first.getId());
        }
        boolean expectedSubmission = campaignEnabled && change == CampaignChange.NONE;

        assertThat(workspaceResolver
                        .resolveForRepository(repository.getNameWithOwner())
                        .orElseThrow()
                        .getId())
                .isEqualTo(first.getId());
        ReviewBackfillSubmitter submitter = beanFactory.createBean(ReviewBackfillSubmitter.class);
        assertThat(AopUtils.isAopProxy(submitter)).isTrue();
        assertThat(submitter.offer(run, work.getId()))
                .isEqualTo(
                        expectedSubmission
                                ? ReviewBackfillSubmitter.Outcome.SUBMITTED
                                : ReviewBackfillSubmitter.Outcome.PASSED);

        String artifactIdField = work instanceof PullRequest ? "pull_request_id" : "issue_id";
        String jobsForWork = """
                SELECT id FROM agent_job
                WHERE workspace_id = ? AND artifact_kind = ? AND metadata ->> ? = ?
                """;
        assertThat(jdbcTemplate.queryForList(
                        jobsForWork,
                        UUID.class,
                        first.getId(),
                        kind.value(),
                        artifactIdField,
                        work.getId().toString()))
                .isEmpty();
        var campaignJobIds = jdbcTemplate.queryForList(
                jobsForWork,
                UUID.class,
                campaign.getId(),
                kind.value(),
                artifactIdField,
                work.getId().toString());
        assertThat(signalRepository.findForArtifact(first.getId(), kind.value(), work.getId()))
                .isEmpty();
        assertThat(signalRepository.findForArtifact(campaign.getId(), kind.value(), work.getId()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getWorkspace().getId()).isEqualTo(campaign.getId());
                    assertThat(row.getArtifactKind()).isEqualTo(kind.value());
                    assertThat(row.getArtifactId()).isEqualTo(work.getId());
                    assertThat(row.getSignalName()).isEqualTo(signal.value());
                    assertThat(row.getRevision()).isEqualTo(key.revision().value());
                    assertThat(row.getDiscoveredVia()).isEqualTo(DiscoveredVia.BACKFILL);
                    if (expectedSubmission) {
                        assertThat(campaignJobIds).hasSize(1);
                        UUID jobId = campaignJobIds.getFirst();
                        assertThat(row.getState()).isEqualTo(SignalState.TRIGGERED);
                        assertThat(row.getStateReason()).isNull();
                        assertThat(row.getJobId()).isEqualTo(jobId);
                        var job = jobRepository
                                .findByIdAndWorkspaceId(jobId, campaign.getId())
                                .orElseThrow();
                        assertThat(job.getWorkspace().getId()).isEqualTo(campaign.getId());
                        assertThat(job.getArtifactKind()).isEqualTo(kind);
                        assertThat(job.getJobType())
                                .isEqualTo(
                                        work instanceof PullRequest
                                                ? AgentJobType.PULL_REQUEST_REVIEW
                                                : AgentJobType.ISSUE_REVIEW);
                        assertThat(job.getIntegrationKind()).isEqualTo(IntegrationKind.GITHUB);
                        assertThat(job.getPracticeTriggerMode()).isEqualTo(TriggerMode.MANUAL);
                        var metadata = Objects.requireNonNull(job.getMetadata());
                        assertThat(metadata.path(artifactIdField).asLong()).isEqualTo(work.getId());
                        assertThat(metadata.path(ReviewOutputService.ORIGIN_METADATA_KEY)
                                        .asString())
                                .isEqualTo("BACKFILL");
                        assertThat(jobRepository.findByIdAndWorkspaceId(jobId, first.getId()))
                                .isEmpty();
                    } else {
                        assertThat(campaignJobIds).isEmpty();
                        assertThat(row.getState()).isEqualTo(SignalState.SUPPRESSED);
                        assertThat(row.getStateReason())
                                .isEqualTo(
                                        change == CampaignChange.REMOVE_MONITOR
                                                ? SignalStateReason.OUT_OF_REVIEW_SCOPE
                                                : SignalStateReason.GATE_SKIPPED);
                        assertThat(row.getJobId()).isNull();
                    }
                });
    }

    private void makeReviewable(Workspace target, SignalName signal, boolean manualEnabled) {
        Workspace stored = workspaceRepository.findById(target.getId()).orElseThrow();
        stored.getFeatures().setPracticesEnabled(true);
        stored.getFeatures().setPracticeReviewManualTriggerEnabled(manualEnabled);
        workspaceRepository.saveAndFlush(stored);

        Practice practice = new Practice();
        practice.setWorkspace(stored);
        practice.setSlug("describes-the-work");
        practice.setName("Describes the work");
        practice.setCriteria("The description says what changed and why.");
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(signal.artifactKind()));
        PracticeTestEvidence.configure(practice, signal);
        practice.setAutonomy(PracticeAutonomy.AUTOMATIC);
        practiceRepository.saveAndFlush(practice);

        WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
        connection.setWorkspace(stored);
        connection.setSlug("review-connection");
        connection.setDisplayName("Review connection");
        connection.setBaseUrl("https://api.openai.com");
        connection.setApiProtocol("openai-completions");
        connection.setEnabled(true);
        connection = llmConnectionRepository.saveAndFlush(connection);
        WorkspaceLlmModel model = new WorkspaceLlmModel();
        model.setWorkspace(stored);
        model.setConnection(connection);
        model.setSlug("review-model");
        model.setDisplayName("Review model");
        model.setUpstreamModelId("gpt-5");
        model.setEnabled(true);
        model = llmModelRepository.saveAndFlush(model);
        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setWorkspace(stored);
        binding.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        binding.setEnabled(true);
        binding.setWorkspaceModel(model);
        binding.setTimeoutSeconds(300);
        bindingRepository.saveAndFlush(binding);
    }

    private Issue persistReviewedWork(Repository repository, User author, ArtifactKind kind) {
        Issue work;
        if (ArtifactKinds.PULL_REQUEST.equals(kind)) {
            PullRequest pr = new PullRequest();
            pr.setDraft(false);
            pr.setMerged(false);
            pr.setHeadRefName("feature/backfill");
            pr.setHeadRefOid("a".repeat(40));
            pr.setBaseRefName("main");
            pr.setBaseRefOid("b".repeat(40));
            work = pr;
        } else {
            work = new Issue();
        }
        work.setNativeId(9201L);
        work.setProvider(ensureGitHubProvider());
        work.setRepository(repository);
        work.setAuthor(author);
        work.setNumber(77);
        work.setTitle("A change worth reviewing");
        work.setBody("The description says what changed and why.");
        work.setState(Issue.State.OPEN);
        work.setHtmlUrl(repository.getHtmlUrl() + (work instanceof PullRequest ? "/pull/" : "/issues/") + 77);
        work.setCreatedAt(FROM.plusSeconds(60));
        work.setUpdatedAt(FROM.plusSeconds(120));
        return work instanceof PullRequest pr
                ? pullRequestRepository.saveAndFlush(pr)
                : issueRepository.saveAndFlush(work);
    }

    private String preflight(Workspace workspace) {
        byte[] body = webTestClient
                .post()
                .uri("/workspaces/{slug}/practices/backfill-runs", workspace.getWorkspaceSlug())
                .headers(ReviewBackfillControllerIntegrationTest::asAdminAccount)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(window())
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .returnResult()
                .getResponseBody();
        assertThat(body).isNotNull();
        return runRepository
                .findByWorkspaceIdOrderByCreatedAtDesc(workspace.getId(), PageRequest.ofSize(1))
                .getFirst()
                .getId()
                .toString();
    }

    private WebTestClient.ResponseSpec patchStatus(Workspace workspace, String runId, String status) {
        return webTestClient
                .patch()
                .uri("/workspaces/{slug}/practices/backfill-runs/{runId}/status", workspace.getWorkspaceSlug(), runId)
                .headers(ReviewBackfillControllerIntegrationTest::asAdminAccount)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("status", status))
                .exchange();
    }
}
