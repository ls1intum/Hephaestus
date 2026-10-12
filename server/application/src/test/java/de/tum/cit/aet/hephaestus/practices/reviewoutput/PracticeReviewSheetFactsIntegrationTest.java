package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobCancellationReason;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageEvent;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageEventRepository;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageJobType;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.CheckState;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRevisionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSource;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackSuppressionReason;
import de.tum.cit.aet.hephaestus.practices.feedback.ProposedPlacement;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.model.PracticeRevision;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * The facts the Practice reviews sheets read beyond the record itself: a feedback's deliveries, a review's cost per
 * attempt, cancellation and trace id, a work's provider facts, a practice's versions and an observation's origin.
 * Each is a workspace admin's, and none reads another workspace's rows.
 */
class PracticeReviewSheetFactsIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FEEDBACK = "/workspaces/{slug}/practices/reviews/feedback/{id}";
    private static final String OBSERVATION = "/workspaces/{slug}/practices/reviews/observations/{id}";
    private static final String WORK = "/workspaces/{slug}/practices/reviews/work/{kind}/{id}";
    private static final String JOB = "/workspaces/{slug}/agents/jobs/{id}";
    private static final String REVISIONS = "/workspaces/{slug}/practices/{practiceSlug}/revisions";
    private static final AtomicLong NATIVE_IDS = new AtomicLong(990_000);

    @Autowired
    private ConnectionService sourceConnectionService;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeRevisionRepository revisionRepository;

    @Autowired
    private AgentJobRepository agentJobRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private ObservationRepository observationRepository;

    @Autowired
    private LlmUsageEventRepository usageRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private ConnectionRepository connectionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Workspace workspace;
    private Workspace otherWorkspace;
    private Practice practice;
    private Practice otherPractice;
    private AgentJob job;
    private AgentJob otherJob;
    private User alice;

    @BeforeEach
    void setUpWorkspaces() {
        User owner = persistUser("sheet-facts-owner");
        workspace = createWorkspace("sheet-facts-ws", "Sheet Facts WS", "sheet-facts-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        alice = persistUser("alice");
        ensureWorkspaceMembership(workspace, alice, WorkspaceMembership.WorkspaceRole.MEMBER);
        ensureWorkspaceMembership(workspace, persistUser("testuser"), WorkspaceMembership.WorkspaceRole.MEMBER);
        practice = persistPractice(workspace, "pr-description-quality");
        job = persistJob(workspace);

        User otherOwner = persistUser("sheet-facts-other-owner");
        otherWorkspace = createWorkspace(
                "sheet-facts-other-ws", "Other WS", "sheet-facts-other-org", AccountType.ORG, otherOwner);
        otherPractice = persistPractice(otherWorkspace, "only-in-the-other-workspace");
        otherJob = persistJob(otherWorkspace);
    }

    private Practice persistPractice(Workspace ws, String slug) {
        Practice created = new Practice();
        created.setAutomatedReviewPolicy(PracticeTestEvidence.pullRequest());
        created.setWorkspace(ws);
        created.setSlug(slug);
        created.setName("PR Description Quality");
        created.setCriteria("Criteria for " + slug);
        PracticeTestEvidence.configure(created, ScmSignals.PULL_REQUEST_OPENED);
        created.setAutonomy(PracticeAutonomy.AUTOMATIC);
        return practiceRepository.save(created);
    }

    private AgentJob persistJob(Workspace ws) {
        AgentJob agentJob = new AgentJob();
        agentJob.setWorkspace(ws);
        agentJob.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        agentJob.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        agentJob.setConfigSnapshot(OBJECT_MAPPER.valueToTree(Map.of("model", "test")));
        agentJob.setEvidenceSnapshot(OBJECT_MAPPER.valueToTree(Map.of("manifest", Map.of("contractVersion", "1.2.0"))));
        return agentJobRepository.save(agentJob);
    }

    private Feedback compose(Workspace ws, AgentJob agentJob, FeedbackChannel channel, boolean proposal) {
        return feedbackRepository.save(Feedback.builder()
                .agentJobId(agentJob.getId())
                .workspaceId(ws.getId())
                .artifactKind(ArtifactKinds.PULL_REQUEST)
                .artifactId(7L)
                .recipientUserId(alice.getId())
                .aboutUserId(alice.getId())
                .channel(channel)
                .position(0)
                .deliveryState(proposal ? FeedbackDeliveryState.DELIVERED : FeedbackDeliveryState.PREPARED)
                .body("Feedback")
                .proposedPlacements(proposal ? List.of(ProposedPlacement.summary("Summary")) : List.of())
                .source(FeedbackSource.AGENT)
                .createdAt(Instant.now())
                .build());
    }

    /** A delivery of {@code agentJob}'s package, or of {@code feedback}'s approved package when it is named. */
    private UUID dispatch(
            Workspace ws,
            AgentJob agentJob,
            @Nullable Feedback feedback,
            String state,
            int attempts,
            @Nullable String lastError,
            @Nullable String url) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO feedback_dispatch (
                    id, destination_key, workspace_id, agent_job_id, feedback_id, destination, state, body,
                    practice_slugs, package_content, delivered_placements, write_started, inline_write_started,
                    next_attempt_at, attempt_count, last_error, delivered_external_url, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'Summary', '[]'::jsonb, '{}'::jsonb, '[]'::jsonb, FALSE, FALSE, ?, ?, ?,
                    ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                id,
                feedback == null ? "review:" + agentJob.getId() : "approved:" + feedback.getId(),
                ws.getId(),
                agentJob.getId(),
                feedback == null ? null : feedback.getId(),
                feedback == null ? "AUTOMATIC_REVIEW_PACKAGE" : "APPROVED_REVIEW_PACKAGE",
                state,
                Timestamp.from(Instant.parse("2026-10-07T10:40:00Z")),
                attempts,
                lastError,
                url);
        return id;
    }

    private WebTestClient.ResponseSpec get(String uri, Object... uriVariables) {
        return webTestClient
                .get()
                .uri(uri, uriVariables)
                .headers(TestAuthUtils.withCurrentUser())
                .exchange();
    }

    private WebTestClient.BodyContentSpec getOk(String uri, Object... uriVariables) {
        return get(uri, uriVariables).expectStatus().isOk().expectBody();
    }

    @Nested
    @DisplayName("Feedback deliveries")
    class FeedbackDeliveries {

        @Test
        @WithAdminUser
        void shouldListTheReviewPackageThatCarriedFeedbackOnTheWork() {
            Feedback feedback = compose(workspace, job, FeedbackChannel.IN_CONTEXT, false);
            UUID id = dispatch(workspace, job, null, "PENDING", 3, "502 Bad Gateway", null);

            getOk(FEEDBACK, workspace.getWorkspaceSlug(), feedback.getId())
                    .jsonPath("$.dispatches.length()")
                    .isEqualTo(1)
                    .jsonPath("$.dispatches[0].id")
                    .isEqualTo(id.toString())
                    .jsonPath("$.dispatches[0].destination")
                    .isEqualTo("AUTOMATIC_REVIEW_PACKAGE")
                    .jsonPath("$.dispatches[0].state")
                    .isEqualTo("PENDING")
                    .jsonPath("$.dispatches[0].attemptCount")
                    .isEqualTo(3)
                    .jsonPath("$.dispatches[0].nextAttemptAt")
                    .isEqualTo("2026-10-07T10:40:00Z")
                    .jsonPath("$.dispatches[0].lastError")
                    .isEqualTo("502 Bad Gateway");
        }

        @Test
        @WithAdminUser
        void shouldNotListTheSentReviewPackageForFeedbackItLeftOut() {
            compose(workspace, job, FeedbackChannel.IN_CONTEXT, false);
            // A sibling the composer withheld, so the package that went out did not carry it.
            Feedback withheld = feedbackRepository.save(Feedback.builder()
                    .agentJobId(job.getId())
                    .workspaceId(workspace.getId())
                    .artifactKind(ArtifactKinds.PULL_REQUEST)
                    .artifactId(7L)
                    .recipientUserId(alice.getId())
                    .aboutUserId(alice.getId())
                    .channel(FeedbackChannel.IN_CONTEXT)
                    .position(2000)
                    .deliveryState(FeedbackDeliveryState.SUPPRESSED)
                    .suppressionReason(FeedbackSuppressionReason.VOLUME_CAPPED)
                    .source(FeedbackSource.AGENT)
                    .createdAt(Instant.now())
                    .build());
            dispatch(workspace, job, null, "SENT", 1, null, "https://github.com/acme/repo/pull/1#review");

            getOk(FEEDBACK, workspace.getWorkspaceSlug(), withheld.getId())
                    .jsonPath("$.dispatches.length()")
                    .isEqualTo(0);
        }

        @Test
        @WithAdminUser
        void shouldListOnlyTheApprovedProposalsOwnPackageAndNoNextAttemptOnceSent() {
            Feedback proposal = compose(workspace, job, FeedbackChannel.IN_CONTEXT, true);
            dispatch(workspace, job, null, "SENT", 1, null, "https://github.com/acme/repo/pull/1#review");
            dispatch(workspace, job, proposal, "SENT", 2, null, "https://github.com/acme/repo/pull/1#approved");

            getOk(FEEDBACK, workspace.getWorkspaceSlug(), proposal.getId())
                    .jsonPath("$.dispatches.length()")
                    .isEqualTo(1)
                    .jsonPath("$.dispatches[0].destination")
                    .isEqualTo("APPROVED_REVIEW_PACKAGE")
                    .jsonPath("$.dispatches[0].deliveredExternalUrl")
                    .isEqualTo("https://github.com/acme/repo/pull/1#approved")
                    .jsonPath("$.dispatches[0].nextAttemptAt")
                    .doesNotExist();
        }

        @Test
        @WithAdminUser
        void shouldListNoDeliveryForPracticePageFeedback() {
            Feedback feedback = compose(workspace, job, FeedbackChannel.IN_APP, false);
            dispatch(workspace, job, null, "SENT", 1, null, null);

            getOk(FEEDBACK, workspace.getWorkspaceSlug(), feedback.getId())
                    .jsonPath("$.dispatches.length()")
                    .isEqualTo(0);
        }

        @Test
        @WithAdminUser
        void shouldNotReadAnotherWorkspacesFeedbackOrDeliveries() {
            Feedback theirs = compose(otherWorkspace, otherJob, FeedbackChannel.IN_CONTEXT, false);
            dispatch(otherWorkspace, otherJob, null, "FAILED", 5, "Theirs", null);
            Feedback ours = compose(workspace, job, FeedbackChannel.IN_CONTEXT, false);

            get(FEEDBACK, workspace.getWorkspaceSlug(), theirs.getId())
                    .expectStatus()
                    .isNotFound()
                    .expectBody(Void.class);
            getOk(FEEDBACK, workspace.getWorkspaceSlug(), ours.getId())
                    .jsonPath("$.dispatches.length()")
                    .isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Review cost, cancellation and trace id")
    class ReviewFacts {

        private void usage(Workspace ws, AgentJob agentJob, int attempt, PricingState pricing, @Nullable String cost) {
            LlmUsageEvent event = new LlmUsageEvent();
            event.setId(UUID.randomUUID());
            event.setWorkspace(ws);
            event.setJobType(LlmUsageJobType.PULL_REQUEST_REVIEW);
            event.setSourceType(LlmUsageSourceType.AGENT_JOB);
            event.setSourceId(agentJob.getId());
            event.setSourceAttempt(attempt);
            event.setModel("gpt-test");
            event.setInputTokens(1000L * attempt);
            event.setOutputTokens(100);
            event.setTotalCalls(4);
            event.setPricingState(pricing);
            event.setFundingSource(FundingSource.INSTANCE);
            event.setCostUsd(cost == null ? null : new BigDecimal(cost));
            event.setOccurredAt(Instant.parse("2026-10-07T10:00:00Z").plusSeconds(attempt));
            usageRepository.save(event);
        }

        @Test
        @WithAdminUser
        void shouldListTheCostOfEveryAttemptAndTheirTotal() {
            usage(workspace, job, 2, PricingState.PRICED, "0.0300");
            usage(workspace, job, 1, PricingState.PRICED, "0.0125");

            getOk(JOB + "/usage", workspace.getWorkspaceSlug(), job.getId())
                    .jsonPath("$.attempts.length()")
                    .isEqualTo(2)
                    .jsonPath("$.attempts[0].attempt")
                    .isEqualTo(1)
                    .jsonPath("$.attempts[0].inputTokens")
                    .isEqualTo(1000)
                    .jsonPath("$.attempts[1].costUsd")
                    .isEqualTo(0.03)
                    .jsonPath("$.totalCostUsd")
                    .isEqualTo(0.0425)
                    .jsonPath("$.unpricedAttempts")
                    .isEqualTo(0);
        }

        @Test
        @WithAdminUser
        void shouldLeaveTheTotalOutWhileAnAttemptHasNoPrice() {
            usage(workspace, job, 1, PricingState.PRICED, "0.0125");
            usage(workspace, job, 2, PricingState.UNPRICED, null);

            getOk(JOB + "/usage", workspace.getWorkspaceSlug(), job.getId())
                    .jsonPath("$.attempts[1].pricingState")
                    .isEqualTo("UNPRICED")
                    .jsonPath("$.attempts[1].costUsd")
                    .doesNotExist()
                    .jsonPath("$.totalCostUsd")
                    .doesNotExist()
                    .jsonPath("$.unpricedAttempts")
                    .isEqualTo(1);
        }

        @Test
        @WithAdminUser
        void shouldNotReadTheUsageOfAnotherWorkspacesJob() {
            usage(otherWorkspace, otherJob, 1, PricingState.PRICED, "1.00");

            get(JOB + "/usage", workspace.getWorkspaceSlug(), otherJob.getId())
                    .expectStatus()
                    .isNotFound()
                    .expectBody(Void.class);
        }

        @Test
        @WithUser
        void shouldForbidTheUsageToAWorkspaceMember() {
            get(JOB + "/usage", workspace.getWorkspaceSlug(), job.getId())
                    .expectStatus()
                    .isForbidden()
                    .expectBody(Void.class);
        }

        @Test
        @WithAdminUser
        void shouldSayWhyAJobWasCancelledAndWhichTraceItLogged() {
            job.setStatus(AgentJobStatus.CANCELLED);
            job.setCancellationReason(AgentJobCancellationReason.BUDGET_EXHAUSTED);
            agentJobRepository.save(job);
            String traceId =
                    agentJobRepository.findById(job.getId()).orElseThrow().getTraceId();

            getOk(JOB, workspace.getWorkspaceSlug(), job.getId())
                    .jsonPath("$.cancellationReason")
                    .isEqualTo("BUDGET_EXHAUSTED")
                    .jsonPath("$.traceId")
                    .isEqualTo(traceId)
                    .jsonPath("$.jobToken")
                    .doesNotExist()
                    .jsonPath("$.idempotencyKey")
                    .doesNotExist();
        }
    }

    @Nested
    @DisplayName("Work facts")
    class WorkFacts {

        private void connect(Workspace owner, IntegrationKind kind, ConnectionConfig config) {
            Connection connection = connectionRepository.saveAndFlush(new Connection(owner, kind, kind.name(), config));
            sourceConnectionService.transition(
                    connection,
                    new ConnectionService.TransitionRequest(
                            IntegrationState.ACTIVE,
                            "TEST_CONNECTED",
                            "SYSTEM",
                            "work-facts-test",
                            UUID.randomUUID().toString(),
                            "Source connected"));
        }

        private PullRequest pullRequest(Workspace monitoredBy) {
            long nativeId = NATIVE_IDS.incrementAndGet();
            connect(monitoredBy, IntegrationKind.GITHUB, new ConnectionConfig.GitHubPatConfig(null, null, Set.of()));
            return pullRequest(monitoredBy, ensureGitHubProvider(), "sheet-facts-org/repo-" + nativeId, null);
        }

        /** A pull request or merge request in {@code nameWithOwner} on {@code provider}, which the workspace monitors. */
        private PullRequest pullRequest(
                Workspace monitoredBy,
                IdentityProvider provider,
                String nameWithOwner,
                @Nullable Long monitoredNativeId) {
            long nativeId = NATIVE_IDS.incrementAndGet();
            Repository repository = new Repository();
            repository.setNativeId(nativeId);
            repository.setProvider(provider);
            repository.setName(nameWithOwner.substring(nameWithOwner.indexOf('/') + 1));
            repository.setNameWithOwner(nameWithOwner);
            repository.setHtmlUrl(provider.getServerUrl() + "/" + nameWithOwner);
            repository.setDefaultBranch("main");
            repository = repositoryRepository.save(repository);
            RepositoryToMonitor monitor = new RepositoryToMonitor();
            monitor.setWorkspace(monitoredBy);
            monitor.setNameWithOwner(nameWithOwner);
            monitor.setNativeId(monitoredNativeId);
            repositoryToMonitorRepository.save(monitor);
            PullRequest work = new PullRequest();
            work.setNativeId(nativeId);
            work.setProvider(provider);
            work.setNumber((int) (nativeId % 100_000));
            work.setTitle("Pull request " + nativeId);
            work.setState(Issue.State.CLOSED);
            work.setMerged(true);
            work.setMergedAt(Instant.parse("2026-10-06T15:00:00Z"));
            work.setClosedAt(Instant.parse("2026-10-06T15:00:00Z"));
            work.setDraft(false);
            work.setHeadRefName("feature/sheets");
            work.setBaseRefName("main");
            work.setHeadRefOid("a".repeat(40));
            work.setAdditions(120);
            work.setDeletions(30);
            work.setChangedFiles(7);
            work.setCommits(3);
            work.setHtmlUrl(repository.getHtmlUrl() + "/pull/" + nativeId);
            work.setRepository(repository);
            work.setAuthor(alice);
            work.setCreatedAt(Instant.parse("2026-10-01T09:00:00Z"));
            work.setUpdatedAt(Instant.parse("2026-10-06T15:00:00Z"));
            PullRequest saved = pullRequestRepository.save(work);
            jdbcTemplate.update("UPDATE issue SET head_check_state = 'FAILURE' WHERE id = ?", saved.getId());
            return saved;
        }

        @Test
        @WithAdminUser
        void shouldReturnWhatTheProviderReportedAboutAPullRequest() {
            PullRequest work = pullRequest(workspace);

            getOk(WORK, workspace.getWorkspaceSlug(), "scm.pull_request", work.getId())
                    .jsonPath("$.author.login")
                    .isEqualTo("alice")
                    .jsonPath("$.state")
                    .isEqualTo("MERGED")
                    .jsonPath("$.draft")
                    .isEqualTo(false)
                    .jsonPath("$.sourceBranch")
                    .isEqualTo("feature/sheets")
                    .jsonPath("$.targetBranch")
                    .isEqualTo("main")
                    .jsonPath("$.mergedAt")
                    .isEqualTo("2026-10-06T15:00:00Z")
                    .jsonPath("$.createdAt")
                    .isEqualTo("2026-10-01T09:00:00Z")
                    .jsonPath("$.additions")
                    .isEqualTo(120)
                    .jsonPath("$.deletions")
                    .isEqualTo(30)
                    .jsonPath("$.changedFiles")
                    .isEqualTo(7)
                    .jsonPath("$.checkState")
                    .isEqualTo(CheckState.FAILURE.name());
        }

        @Test
        @WithAdminUser
        void shouldReportNothingAboutWorkInARepositoryOnlyAnotherWorkspaceMonitors() {
            PullRequest theirs = pullRequest(otherWorkspace);

            get(WORK, workspace.getWorkspaceSlug(), "scm.pull_request", theirs.getId())
                    .expectStatus()
                    .isNoContent()
                    .expectBody(Void.class);
        }

        /**
         * This workspace monitors {@code shared-org/shared-repo} on GitHub, and the other workspace monitors a GitLab
         * project with the same name. A merge request of the other workspace must read like work that does not exist.
         */
        private PullRequest sameNameMergeRequestOfTheOtherWorkspace(boolean nativeIdKnown) {
            String nameWithOwner = "shared-org/shared-repo";
            connect(workspace, IntegrationKind.GITHUB, new ConnectionConfig.GitHubPatConfig(null, null, Set.of()));
            connect(
                    otherWorkspace,
                    IntegrationKind.GITLAB,
                    new ConnectionConfig.GitLabConfig(
                            "https://gitlab.com",
                            null,
                            null,
                            ConnectionConfig.GitLabConfig.SigningMode.WHSEC,
                            Set.of(),
                            null));
            PullRequest ours = pullRequest(workspace, ensureGitHubProvider(), nameWithOwner, null);
            if (nativeIdKnown) {
                jdbcTemplate.update("""
                        UPDATE repository_to_monitor SET native_id = (
                            SELECT r.native_id FROM issue i JOIN repository r ON r.id = i.repository_id WHERE i.id = ?)
                        WHERE workspace_id = ?
                        """, ours.getId(), workspace.getId());
            }
            getOk(WORK, workspace.getWorkspaceSlug(), "scm.pull_request", ours.getId())
                    .jsonPath("$.sourceBranch")
                    .isEqualTo("feature/sheets");
            return pullRequest(otherWorkspace, ensureGitLabProvider(), nameWithOwner, null);
        }

        @Test
        @WithAdminUser
        void shouldReportNothingAboutAnotherProvidersWorkWithTheSameRepositoryName() {
            PullRequest theirs = sameNameMergeRequestOfTheOtherWorkspace(true);

            get(WORK, workspace.getWorkspaceSlug(), "scm.pull_request", theirs.getId())
                    .expectStatus()
                    .isNoContent()
                    .expectBody(Void.class);
            get(WORK, workspace.getWorkspaceSlug(), "scm.pull_request", Long.MAX_VALUE)
                    .expectStatus()
                    .isNoContent()
                    .expectBody(Void.class);
        }

        @Test
        @WithAdminUser
        void shouldReportNothingAboutAnotherProvidersWorkBeforeTheMonitorKnowsItsRepositoryId() {
            PullRequest theirs = sameNameMergeRequestOfTheOtherWorkspace(false);

            get(WORK, workspace.getWorkspaceSlug(), "scm.pull_request", theirs.getId())
                    .expectStatus()
                    .isNoContent()
                    .expectBody(Void.class);
        }

        @Test
        @WithAdminUser
        void shouldReportNothingAboutWorkTheProviderDeleted() {
            PullRequest work = pullRequest(workspace);
            jdbcTemplate.update("UPDATE issue SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", work.getId());

            get(WORK, workspace.getWorkspaceSlug(), "scm.pull_request", work.getId())
                    .expectStatus()
                    .isNoContent()
                    .expectBody(Void.class);
        }

        @Test
        @WithAdminUser
        void shouldNotReadAPullRequestAsAnIssue() {
            PullRequest work = pullRequest(workspace);

            get(WORK, workspace.getWorkspaceSlug(), "scm.issue", work.getId())
                    .expectStatus()
                    .isNoContent()
                    .expectBody(Void.class);
        }

        @Test
        @WithAdminUser
        void shouldNotFindFactsForWorkThatIsNotAPullRequestOrIssue() {
            get(WORK, workspace.getWorkspaceSlug(), "chat.conversation_thread", 1L)
                    .expectStatus()
                    .isNotFound();
        }

        @Test
        @WithUser
        void shouldForbidTheFactsToAWorkspaceMember() {
            PullRequest work = pullRequest(workspace);

            get(WORK, workspace.getWorkspaceSlug(), "scm.pull_request", work.getId())
                    .expectStatus()
                    .isForbidden()
                    .expectBody(Void.class);
        }
    }

    @Nested
    @DisplayName("Practice versions")
    class PracticeVersions {

        @Test
        @WithAdminUser
        void shouldListEveryVersionNewestFirstWithWhatChanged() {
            revisionRepository.save(new PracticeRevision(practice, 1));
            practice.setWhyItMatters("Reviewers read the description first.");
            practice = practiceRepository.save(practice);
            revisionRepository.save(new PracticeRevision(practice, 2));
            practice.setCriteria("A description says what changed and why.");
            practice = practiceRepository.save(practice);
            revisionRepository.save(new PracticeRevision(practice, 3));

            getOk(REVISIONS, workspace.getWorkspaceSlug(), practice.getSlug())
                    .jsonPath("$.length()")
                    .isEqualTo(3)
                    .jsonPath("$[0].revisionNumber")
                    .isEqualTo(3)
                    .jsonPath("$[0].changed[0]")
                    .isEqualTo("CRITERIA")
                    .jsonPath("$[0].reviewRulesChanged")
                    .isEqualTo(true)
                    .jsonPath("$[1].changed[0]")
                    .isEqualTo("WHY_IT_MATTERS")
                    .jsonPath("$[1].reviewRulesChanged")
                    .isEqualTo(false)
                    .jsonPath("$[2].changed.length()")
                    .isEqualTo(0);
        }

        @Test
        @WithAdminUser
        void shouldNotListAnotherWorkspacesPractice() {
            revisionRepository.save(new PracticeRevision(otherPractice, 1));

            get(REVISIONS, workspace.getWorkspaceSlug(), otherPractice.getSlug())
                    .expectStatus()
                    .isNotFound()
                    .expectBody(Void.class);
        }

        @Test
        @WithUser
        void shouldForbidTheVersionsToAWorkspaceMember() {
            get(REVISIONS, workspace.getWorkspaceSlug(), practice.getSlug())
                    .expectStatus()
                    .isForbidden()
                    .expectBody(Void.class);
        }
    }

    @Nested
    @DisplayName("Observation origin")
    class ObservationOrigin {

        @Test
        @WithAdminUser
        void shouldSayAnObservationCameFromARequestedReview() {
            UUID id = UUID.randomUUID();
            observationRepository.insertIfAbsent(
                    id,
                    "occurrence-" + id,
                    job.getId(),
                    workspace.getId(),
                    practice.getId(),
                    null,
                    ArtifactKinds.PULL_REQUEST.value(),
                    7L,
                    alice.getId(),
                    "Requested",
                    "NOT_MET",
                    "MAJOR",
                    "{}",
                    "Reasoning",
                    "recurrence-" + id,
                    Instant.now(),
                    "MANUAL");

            getOk(OBSERVATION, workspace.getWorkspaceSlug(), id)
                    .jsonPath("$.origin")
                    .isEqualTo("MANUAL");
        }
    }
}
