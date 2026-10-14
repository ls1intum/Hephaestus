package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.agent.context.providers.ReviewRepositoryPreparer;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.events.EventContext;
import de.tum.cit.aet.hephaestus.integration.core.events.RepositoryRef;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmDomainEvent;
import de.tum.cit.aet.hephaestus.integration.core.events.ScmEventPayload;
import de.tum.cit.aet.hephaestus.integration.core.framework.IntegrationManifestRegistry;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactSignalRepository;
import de.tum.cit.aet.hephaestus.integration.core.signal.DiscoveredVia;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalKey;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalName;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRecorder;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalRevision;
import de.tum.cit.aet.hephaestus.integration.core.signal.SignalState;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.DataSource;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.IssueRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequestRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReviewRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.Repository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.RepositoryRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.IssueEvidenceRevision;
import de.tum.cit.aet.hephaestus.integration.scm.domain.signal.ScmSignals;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeTestEvidence;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.practices.review.GateDecision;
import de.tum.cit.aet.hephaestus.practices.review.ReviewGate;
import de.tum.cit.aet.hephaestus.practices.review.TriggerMode;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitor;
import de.tum.cit.aet.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceResolver;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class PullRequestWorkspaceEventsIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String HEAD = "a".repeat(40);
    private static final String JOBS_FOR_WORK =
            "SELECT id FROM agent_job WHERE workspace_id=? AND metadata->>'pull_request_id'=?";

    @Autowired
    private AutowireCapableBeanFactory beanFactory;

    @Autowired
    private WorkspaceRepository workspaces;

    @Autowired
    private WorkspaceResolver resolver;

    @Autowired
    private RepositoryRepository repositories;

    @Autowired
    private RepositoryToMonitorRepository monitors;

    @Autowired
    private PullRequestRepository pullRequests;

    @Autowired
    private IssueRepository issues;

    @Autowired
    private PullRequestReviewRepository reviews;

    @Autowired
    private PracticeRepository practices;

    @Autowired
    private WorkspaceLlmConnectionRepository llmConnections;

    @Autowired
    private WorkspaceLlmModelRepository llmModels;

    @Autowired
    private WorkspaceAgentBindingRepository bindings;

    @Autowired
    private ArtifactSignalRepository signals;

    @Autowired
    private AgentJobRepository jobs;

    @Autowired
    private ReviewGate gate;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @ParameterizedTest(name = "lower-ID workspace automatic review enabled={0}")
    @ValueSource(booleans = {false, true})
    void shouldSubmitOnlyInTheEnabledWorkspaceWhenRepositoryHasTwoMonitors(boolean firstEnabled) {
        PrScenario scenario = sharedPullRequest(firstEnabled, !firstEnabled);
        Workspace first = scenario.first();
        Workspace second = scenario.second();
        Repository repository = scenario.repository();
        PullRequest pr = scenario.pr();
        long prId = pr.getId();
        Workspace enabled = firstEnabled ? first : second;
        Workspace disabled = firstEnabled ? second : first;
        transactions.executeWithoutResult(status -> {
            PullRequest loaded = pullRequests.findByIdWithAllForGate(prId).orElseThrow();
            assertThat(gate.evaluatePullRequest(
                            loaded, enabled.getId(), ScmSignals.PULL_REQUEST_OPENED, TriggerMode.AUTO))
                    .isInstanceOf(GateDecision.Run.class);
            assertThat(gate.evaluatePullRequest(
                            loaded, disabled.getId(), ScmSignals.PULL_REQUEST_OPENED, TriggerMode.AUTO))
                    .isInstanceOf(GateDecision.Skip.class);
        });
        var event = new ScmDomainEvent.PullRequestCreated(
                ScmEventPayload.PullRequestData.from(pr),
                new EventContext(
                        UUID.randomUUID(),
                        Instant.now(),
                        first.getId(),
                        new RepositoryRef(repository.getId(), repository.getNameWithOwner(), "main"),
                        DataSource.WEBHOOK,
                        "opened",
                        UUID.randomUUID().toString(),
                        null));
        // The bean is disabled in tests. Keep its real dependencies, but own the after-commit transaction
        // explicitly so @Async cannot turn persistence assertions into a timing test.
        AgentJobEventListener listener =
                AopTestUtils.getUltimateTargetObject(beanFactory.createBean(AgentJobEventListener.class));
        var dispatch = new TransactionTemplate(Objects.requireNonNull(transactions.getTransactionManager()));
        dispatch.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        dispatch.executeWithoutResult(status -> listener.onPullRequestCreated(event));

        assertThat(jdbc.queryForList(JOBS_FOR_WORK, UUID.class, disabled.getId(), Long.toString(prId)))
                .isEmpty();
        assertThat(signals.findForArtifact(enabled.getId(), ScmSignals.PULL_REQUEST.value(), prId))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getWorkspace().getId()).isEqualTo(enabled.getId());
                    assertThat(row.getArtifactKind()).isEqualTo(ScmSignals.PULL_REQUEST.value());
                    assertThat(row.getArtifactId()).isEqualTo(prId);
                    assertThat(row.getSignalName()).isEqualTo(ScmSignals.PULL_REQUEST_OPENED.value());
                    assertThat(row.getRevision())
                            .isEqualTo(SignalRevision.ofHeadCommit(HEAD).value());
                    assertThat(row.getDiscoveredVia()).isEqualTo(DiscoveredVia.EVENT);
                    assertThat(row.getState()).isEqualTo(SignalState.TRIGGERED);
                    assertThat(row.getStateReason()).isNull();
                    UUID jobId = Objects.requireNonNull(row.getJobId());
                    assertThat(jdbc.queryForList(JOBS_FOR_WORK, UUID.class, enabled.getId(), Long.toString(prId)))
                            .containsExactly(jobId);
                    AgentJob job =
                            jobs.findByIdAndWorkspaceId(jobId, enabled.getId()).orElseThrow();
                    assertThat(job.getWorkspace().getId()).isEqualTo(enabled.getId());
                    assertThat(job.getJobType()).isEqualTo(AgentJobType.PULL_REQUEST_REVIEW);
                    assertThat(job.getArtifactKind()).isEqualTo(ScmSignals.PULL_REQUEST);
                    assertThat(job.getIntegrationKind()).isEqualTo(IntegrationKind.GITHUB);
                    assertThat(job.getPurpose()).isEqualTo(AgentPurpose.PRACTICE_REVIEW);
                    assertThat(job.getPracticeTriggerMode()).isEqualTo(TriggerMode.AUTO);
                    var metadata = Objects.requireNonNull(job.getMetadata());
                    assertThat(metadata.path("pull_request_id").asLong()).isEqualTo(prId);
                    assertThat(metadata.path("repository_id").asLong())
                            .isEqualTo(event.pullRequest().repository().id());
                    assertThat(metadata.path("commit_sha").asString()).isEqualTo(HEAD);
                    assertThat(metadata.path("target_branch").asString()).isEqualTo("main");
                    assertThat(metadata.path(AgentJob.SIGNAL_REVISION_METADATA_KEY)
                                    .asString())
                            .isEqualTo(row.getRevision());
                    assertThat(jobs.findByIdAndWorkspaceId(jobId, disabled.getId()))
                            .isEmpty();
                });
    }

    @Test
    void shouldReviewBothEnabledWorkspacesOnceWhenTheEventIsDeliveredTwice() {
        PrScenario scenario = sharedPullRequest(true, true);
        var event = created(scenario, DataSource.WEBHOOK);
        AgentJobEventListener listener = nativeListener();
        transactions.executeWithoutResult(status -> listener.onPullRequestCreated(event));
        transactions.executeWithoutResult(status -> listener.onPullRequestCreated(event));
        for (Workspace workspace : new Workspace[] {scenario.first(), scenario.second()}) {
            assertThat(signals.findForArtifact(
                            workspace.getId(),
                            ScmSignals.PULL_REQUEST.value(),
                            scenario.pr().getId()))
                    .hasSize(1);
            assertOpened(workspace, scenario.pr());
        }
    }

    @Test
    void shouldRecordSyncForBothWorkspacesWithoutSubmittingReviews() {
        PrScenario scenario = sharedPullRequest(true, true);
        AgentJobEventListener listener = nativeListener();
        var event = created(scenario, DataSource.GRAPHQL_SYNC);
        transactions.executeWithoutResult(status -> listener.onPullRequestCreated(event));
        for (Workspace workspace : new Workspace[] {scenario.first(), scenario.second()}) {
            assertThat(signals.findForArtifact(
                            workspace.getId(),
                            ScmSignals.PULL_REQUEST.value(),
                            scenario.pr().getId()))
                    .singleElement()
                    .satisfies(row -> {
                        assertThat(row.getState()).isEqualTo(SignalState.RECORDED);
                        assertThat(row.getDiscoveredVia()).isEqualTo(DiscoveredVia.SYNC);
                        assertThat(row.getJobId()).isNull();
                    });
            assertThat(jdbc.queryForList(
                            JOBS_FOR_WORK,
                            UUID.class,
                            workspace.getId(),
                            Long.toString(scenario.pr().getId())))
                    .isEmpty();
        }
    }

    @ParameterizedTest(name = "foreign source is a different provider kind={0}")
    @ValueSource(booleans = {true, false})
    void shouldNotRecordPullRequestOccasionsForAnotherProviderOrServer(boolean differentKind) {
        IdentityProvider foreign = differentKind
                ? ensureGitLabProvider()
                : gitProviderRepository.saveAndFlush(
                        new IdentityProvider(IdentityProviderType.GITHUB, "https://github.enterprise.example"));
        PrScenario scenario = sharedPullRequest(true, true, foreign);
        AgentJobEventListener listener = nativeListener();
        transactions.executeWithoutResult(
                status -> listener.onPullRequestCreated(created(scenario, DataSource.WEBHOOK)));
        assertOpened(scenario.first(), scenario.pr());
        assertThat(signals.findForArtifact(
                        scenario.second().getId(),
                        ScmSignals.PULL_REQUEST.value(),
                        scenario.pr().getId()))
                .isEmpty();
        assertThat(jdbc.queryForList(
                        JOBS_FOR_WORK,
                        UUID.class,
                        scenario.second().getId(),
                        Long.toString(scenario.pr().getId())))
                .isEmpty();
    }

    @ParameterizedTest(name = "legacy unknown monitor native ID={0}")
    @ValueSource(booleans = {false, true})
    void shouldRequireKnownNativeIdentityWithoutRefusingLegacyUnknownIdentity(boolean legacyUnknown) {
        PrScenario scenario = sharedPullRequest(true, true);
        var monitor = monitors.findByWorkspaceIdAndNameWithOwner(
                        scenario.second().getId(), scenario.repository().getNameWithOwner())
                .orElseThrow();
        monitor.setNativeId(legacyUnknown ? null : scenario.repository().getNativeId() + 1);
        monitors.saveAndFlush(monitor);
        AgentJobEventListener listener = nativeListener();
        transactions.executeWithoutResult(
                status -> listener.onPullRequestCreated(created(scenario, DataSource.WEBHOOK)));
        assertOpened(scenario.first(), scenario.pr());
        if (legacyUnknown) {
            assertOpened(scenario.second(), scenario.pr());
        } else {
            assertThat(signals.findForArtifact(
                            scenario.second().getId(),
                            ScmSignals.PULL_REQUEST.value(),
                            scenario.pr().getId()))
                    .isEmpty();
        }
    }

    @Test
    void shouldUseTheCurrentRepositoryInsteadOfTheOldEventRepository() {
        PrScenario scenario = sharedPullRequest(true, true);
        var oldEvent = created(scenario, DataSource.WEBHOOK);
        Repository current = new Repository();
        current.setProvider(ensureGitHubProvider());
        current.setNativeId(97102L);
        current.setName("moved");
        current.setNameWithOwner("pr-events-org/moved");
        current.setHtmlUrl("https://github.com/pr-events-org/moved");
        current.setDefaultBranch("main");
        current = repositories.saveAndFlush(current);
        var monitor = monitors.findByWorkspaceIdAndNameWithOwner(
                        scenario.second().getId(), scenario.repository().getNameWithOwner())
                .orElseThrow();
        monitor.setNameWithOwner(current.getNameWithOwner());
        monitor.setNativeId(current.getNativeId());
        monitors.saveAndFlush(monitor);
        PullRequest moved = pullRequests.findById(scenario.pr().getId()).orElseThrow();
        moved.setRepository(current);
        moved = pullRequests.saveAndFlush(moved);
        AgentJobEventListener listener = nativeListener();
        transactions.executeWithoutResult(status -> listener.onPullRequestCreated(oldEvent));
        AgentJob job = assertOpened(scenario.second(), moved);
        assertThat(Objects.requireNonNull(job.getMetadata())
                        .path("repository_id")
                        .asLong())
                .isEqualTo(current.getId());
        assertThat(signals.findForArtifact(scenario.first().getId(), ScmSignals.PULL_REQUEST.value(), moved.getId()))
                .isEmpty();
    }

    @Test
    void shouldAdmitTheLinkedReviewerWithoutSubstitutingThePullRequestAuthor() {
        PrScenario scenario = sharedPullRequest(true, true);
        for (Workspace workspace : new Workspace[] {scenario.first(), scenario.second()}) {
            Practice practice = practices
                    .findByWorkspaceIdAndSlug(workspace.getId(), "describes-the-work")
                    .orElseThrow();
            PracticeTestEvidence.configure(practice, ScmSignals.PULL_REQUEST_REVIEWED);
            practices.saveAndFlush(practice);
        }
        var reviewer = persistUser("linked-reviewer");
        ensureWorkspaceMembership(scenario.second(), reviewer, WorkspaceRole.MEMBER);
        var review = new PullRequestReview();
        review.setProvider(ensureGitHubProvider());
        review.setNativeId(97801L);
        review.setPullRequest(scenario.pr());
        review.setAuthor(reviewer);
        review.setState(PullRequestReview.State.APPROVED);
        review.setBody("The change is ready.");
        review.setHtmlUrl("https://github.com/pr-events-org/shared/pull/77#review-97801");
        review.setSubmittedAt(Instant.now());
        review.setCommitId(HEAD);
        review = reviews.saveAndFlush(review);
        var event = new ScmDomainEvent.ReviewSubmitted(
                ScmEventPayload.ReviewData.from(review).orElseThrow(),
                created(scenario, DataSource.WEBHOOK).context());
        AgentJobEventListener listener = nativeListener();
        transactions.executeWithoutResult(status -> listener.onReviewSubmitted(event));
        assertThat(jdbc.queryForList(
                        JOBS_FOR_WORK,
                        UUID.class,
                        scenario.first().getId(),
                        Long.toString(scenario.pr().getId())))
                .isEmpty();
        var row = signals.findForArtifact(
                        scenario.second().getId(),
                        ScmSignals.PULL_REQUEST.value(),
                        scenario.pr().getId())
                .getFirst();
        assertThat(row.getState()).isEqualTo(SignalState.TRIGGERED);
        assertThat(row.getRevision())
                .isEqualTo(SignalRevision.ofEventId(review.getId()).value());
        AgentJob job = jobs.findByIdAndWorkspaceId(
                        Objects.requireNonNull(row.getJobId()),
                        scenario.second().getId())
                .orElseThrow();
        var metadata = Objects.requireNonNull(job.getMetadata());
        assertThat(metadata.path("review_id").asLong()).isEqualTo(review.getId());
        assertThat(metadata.path("about_user_id").asLong()).isEqualTo(reviewer.getId());
    }

    @ParameterizedTest(name = "mirror transaction commits={0}")
    @ValueSource(booleans = {true, false})
    void shouldCommitOrRollBackBothWorkspaceOccasionsWithTheMirror(boolean commit) {
        PrScenario scenario = sharedPullRequest(true, true);
        AgentJobEventListener listener = nativeListener();
        transactions.executeWithoutResult(status -> {
            PullRequest managed =
                    pullRequests.findByIdWithAllForGate(scenario.pr().getId()).orElseThrow();
            managed.setHeadRefOid("c".repeat(40));
            managed.setBody("The updated description.");
            pullRequests.saveAndFlush(managed);
            var data = ScmEventPayload.PullRequestData.from(managed);
            var context = created(scenario, DataSource.WEBHOOK).context();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    listener.onPullRequestSynchronized(new ScmDomainEvent.PullRequestSynchronized(data, context));
                    listener.onPullRequestUpdated(new ScmDomainEvent.PullRequestUpdated(data, Set.of("body"), context));
                }
            });
            if (!commit) status.setRollbackOnly();
        });
        assertThat(pullRequests.findHeadRefOidById(scenario.pr().getId())).contains(commit ? "c".repeat(40) : HEAD);
        for (Workspace workspace : new Workspace[] {scenario.first(), scenario.second()}) {
            var rows = signals.findForArtifact(
                    workspace.getId(),
                    ScmSignals.PULL_REQUEST.value(),
                    scenario.pr().getId());
            if (commit) {
                assertThat(rows).hasSize(2).allSatisfy(row -> {
                    assertThat(row.getState()).isEqualTo(SignalState.DEFERRED);
                    assertThat(row.getJobId()).isNull();
                });
                assertThat(rows)
                        .extracting(row -> row.getSignalName())
                        .containsExactlyInAnyOrder(
                                ScmSignals.PULL_REQUEST_SYNCHRONIZED.value(), ScmSignals.PULL_REQUEST_EDITED.value());
            } else {
                assertThat(rows).isEmpty();
            }
            assertThat(jdbc.queryForList(
                            JOBS_FOR_WORK,
                            UUID.class,
                            workspace.getId(),
                            Long.toString(scenario.pr().getId())))
                    .isEmpty();
        }
    }

    @Test
    void shouldCommitTheSecondWorkspaceWhenTheFirstWorkspaceDatabaseTransactionFails() {
        PrScenario scenario = sharedPullRequest(true, true);
        SignalRecorder recorder = beanFactory.getBean(SignalRecorder.class);
        SignalRecorder failing = mock(SignalRecorder.class, delegatesTo(recorder));
        doAnswer(invocation -> {
                    SignalKey key = invocation.getArgument(0);
                    if (key.workspaceId() == scenario.first().getId()) {
                        jdbc.execute("SELECT 1/0");
                    }
                    return recorder.record(key, invocation.getArgument(1), invocation.getArgument(2));
                })
                .when(failing)
                .record(any(), any(), any());
        AgentJobEventListener listener = new AgentJobEventListener(
                beanFactory.getBean(AgentJobService.class),
                pullRequests,
                gate,
                resolver,
                failing,
                beanFactory.getBean(IntegrationManifestRegistry.class),
                beanFactory.getBean(PlatformTransactionManager.class),
                beanFactory.getBean(ReviewRepositoryPreparer.class));
        transactions.executeWithoutResult(
                status -> listener.onPullRequestCreated(created(scenario, DataSource.WEBHOOK)));
        assertThat(signals.findForArtifact(
                        scenario.first().getId(),
                        ScmSignals.PULL_REQUEST.value(),
                        scenario.pr().getId()))
                .isEmpty();
        assertThat(jdbc.queryForList(
                        JOBS_FOR_WORK,
                        UUID.class,
                        scenario.first().getId(),
                        Long.toString(scenario.pr().getId())))
                .isEmpty();
        assertOpened(scenario.second(), scenario.pr());
    }

    @Test
    void shouldNotDispatchAnIssueAfterItsWorkspaceStopsMonitoringBetweenTransactions() {
        PrScenario scenario = sharedPullRequest(true, true);
        for (Workspace workspace : new Workspace[] {scenario.first(), scenario.second()}) {
            Practice practice = practices
                    .findByWorkspaceIdAndSlug(workspace.getId(), "describes-the-work")
                    .orElseThrow();
            PracticeTestEvidence.configure(practice, ScmSignals.ISSUE_OPENED);
            practices.saveAndFlush(practice);
        }
        var issue = new Issue();
        issue.setProvider(ensureGitHubProvider());
        issue.setNativeId(97901L);
        issue.setRepository(scenario.repository());
        issue.setAuthor(scenario.pr().getAuthor());
        issue.setNumber(79);
        issue.setTitle("Describe the shared issue");
        issue.setBody("This request describes the work and expected result.");
        issue.setHtmlUrl(scenario.repository().getHtmlUrl() + "/issues/79");
        issue.setState(Issue.State.OPEN);
        issue.setCreatedAt(Instant.now());
        issue.setUpdatedAt(Instant.now());
        issue = issues.saveAndFlush(issue);
        var data = ScmEventPayload.IssueData.from(issue);
        SignalRecorder recorder = beanFactory.getBean(SignalRecorder.class);
        SignalRecorder removing = mock(SignalRecorder.class, delegatesTo(recorder));
        doAnswer(invocation -> {
                    SignalKey key = invocation.getArgument(0);
                    boolean recorded = recorder.record(key, invocation.getArgument(1), invocation.getArgument(2));
                    if (key.workspaceId() == scenario.first().getId()) {
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                var removal =
                                        new TransactionTemplate(beanFactory.getBean(PlatformTransactionManager.class));
                                removal.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
                                removal.executeWithoutResult(status -> {
                                    var monitor = monitors.findByWorkspaceIdAndNameWithOwner(
                                                    scenario.second().getId(),
                                                    scenario.repository().getNameWithOwner())
                                            .orElseThrow();
                                    monitors.delete(monitor);
                                    monitors.flush();
                                });
                            }
                        });
                    }
                    return recorded;
                })
                .when(removing)
                .record(any(), any(), any());
        var listener = new IssueAgentJobEventListener(
                beanFactory.getBean(AgentJobService.class),
                issues,
                pullRequests,
                gate,
                resolver,
                removing,
                beanFactory.getBean(IssueEvidenceRevision.class),
                beanFactory.getBean(PlatformTransactionManager.class),
                beanFactory.getBean(ReviewRepositoryPreparer.class));
        listener.onIssueCreated(new ScmDomainEvent.IssueCreated(
                data, created(scenario, DataSource.WEBHOOK).context()));
        assertThat(monitors.existsByWorkspaceIdAndNameWithOwner(
                        scenario.second().getId(), scenario.repository().getNameWithOwner()))
                .isFalse();
        assertThat(signals.findForArtifact(scenario.first().getId(), ScmSignals.ISSUE.value(), issue.getId()))
                .singleElement()
                .satisfies(row -> assertThat(row.getState()).isEqualTo(SignalState.TRIGGERED));
        assertThat(signals.findForArtifact(scenario.second().getId(), ScmSignals.ISSUE.value(), issue.getId()))
                .as("the next workspace transaction must use current monitoring authority")
                .isEmpty();
        assertThat(jdbc.queryForList(
                        "SELECT id FROM agent_job WHERE workspace_id=? AND metadata->>'issue_id'=?",
                        UUID.class,
                        scenario.second().getId(),
                        Long.toString(issue.getId())))
                .isEmpty();
    }

    private PrScenario sharedPullRequest(boolean firstEnabled, boolean secondEnabled) {
        return sharedPullRequest(firstEnabled, secondEnabled, ensureGitHubProvider());
    }

    private PrScenario sharedPullRequest(boolean firstEnabled, boolean secondEnabled, IdentityProvider secondProvider) {
        var owner = persistUser("fanout-owner");
        Workspace first = createWorkspace("pr-events-a", "First monitor", "pr-events-a", AccountType.ORG, owner);
        Workspace second = createWorkspace("pr-events-b", "Second monitor", "pr-events-b", AccountType.ORG, owner);
        assertThat(first.getId()).isLessThan(second.getId());
        var author = persistUser("testuser");
        var provider = ensureGitHubProvider();
        Repository repository = new Repository();
        repository.setProvider(provider);
        repository.setNativeId(97101L);
        repository.setName("shared");
        repository.setNameWithOwner("pr-events-org/shared");
        repository.setHtmlUrl("https://github.com/pr-events-org/shared");
        repository.setDefaultBranch("main");
        repository = repositories.saveAndFlush(repository);
        for (Workspace workspace : new Workspace[] {first, second}) {
            connectActivitySource(workspace, workspace.getId().equals(first.getId()) ? provider : secondProvider);
            ensureWorkspaceMembership(workspace, author, WorkspaceRole.MEMBER);
            if (workspace.getId().equals(first.getId()) || Objects.equals(provider.getId(), secondProvider.getId())) {
                assertThat(workspaceMembershipService.isPracticeReviewEligible(workspace.getId(), author.getId()))
                        .isTrue();
            }
            var monitor = new RepositoryToMonitor();
            monitor.setWorkspace(workspace);
            monitor.setNameWithOwner(repository.getNameWithOwner());
            monitor.setNativeId(repository.getNativeId());
            monitors.saveAndFlush(monitor);
            makeReviewable(workspace, workspace.getId().equals(first.getId()) ? firstEnabled : secondEnabled);
        }
        assertThat(resolver.resolveAllForRepository(repository.getNameWithOwner()))
                .extracting(Workspace::getId)
                .containsExactly(first.getId(), second.getId());
        assertThat(resolver.resolveForRepository(repository.getNameWithOwner())
                        .orElseThrow()
                        .getId())
                .isEqualTo(first.getId());
        if (!Objects.equals(provider.getId(), secondProvider.getId())) {
            var foreign = new Repository();
            foreign.setProvider(secondProvider);
            foreign.setNativeId(repository.getNativeId());
            foreign.setName(repository.getName());
            foreign.setNameWithOwner(repository.getNameWithOwner());
            foreign.setHtmlUrl(secondProvider.getServerUrl() + "/" + repository.getNameWithOwner());
            foreign.setDefaultBranch("main");
            repositories.saveAndFlush(foreign);
        }
        PullRequest pr = new PullRequest();
        pr.setProvider(provider);
        pr.setNativeId(97201L);
        pr.setRepository(repository);
        pr.setAuthor(author);
        pr.setNumber(77);
        pr.setTitle("Describe the shared change");
        pr.setBody("This change describes what changed and why.");
        pr.setHtmlUrl(repository.getHtmlUrl() + "/pull/77");
        pr.setState(Issue.State.OPEN);
        pr.setDraft(false);
        pr.setMerged(false);
        pr.setHeadRefName("feature/fanout");
        pr.setHeadRefOid(HEAD);
        pr.setBaseRefName("main");
        pr.setBaseRefOid("b".repeat(40));
        pr.setCreatedAt(Instant.now());
        pr.setUpdatedAt(Instant.now());
        pr = pullRequests.saveAndFlush(pr);
        return new PrScenario(first, second, repository, pr);
    }

    private record PrScenario(Workspace first, Workspace second, Repository repository, PullRequest pr) {}

    private AgentJobEventListener nativeListener() {
        return AopTestUtils.getUltimateTargetObject(beanFactory.createBean(AgentJobEventListener.class));
    }

    private ScmDomainEvent.PullRequestCreated created(PrScenario scenario, DataSource source) {
        return new ScmDomainEvent.PullRequestCreated(
                ScmEventPayload.PullRequestData.from(scenario.pr()),
                new EventContext(
                        UUID.randomUUID(),
                        Instant.now(),
                        scenario.first().getId(),
                        RepositoryRef.from(scenario.repository()),
                        source,
                        "opened",
                        UUID.randomUUID().toString(),
                        null));
    }

    private AgentJob assertOpened(Workspace workspace, PullRequest pr) {
        var row = signals.findForArtifact(workspace.getId(), ScmSignals.PULL_REQUEST.value(), pr.getId())
                .getFirst();
        assertThat(row.getWorkspace().getId()).isEqualTo(workspace.getId());
        assertThat(row.getSignalName()).isEqualTo(ScmSignals.PULL_REQUEST_OPENED.value());
        assertThat(row.getRevision())
                .isEqualTo(SignalRevision.ofHeadCommit(HEAD).value());
        assertThat(row.getState()).isEqualTo(SignalState.TRIGGERED);
        UUID id = Objects.requireNonNull(row.getJobId());
        assertThat(jdbc.queryForList(JOBS_FOR_WORK, UUID.class, workspace.getId(), Long.toString(pr.getId())))
                .containsExactly(id);
        AgentJob job = jobs.findByIdAndWorkspaceId(id, workspace.getId()).orElseThrow();
        assertThat(job.getPracticeTriggerMode()).isEqualTo(TriggerMode.AUTO);
        var snapshot = Objects.requireNonNull(job.getConfigSnapshot());
        assertThat(snapshot.path("workspaceId").asLong()).isEqualTo(workspace.getId());
        assertThat(snapshot.path("connectionScope").asString()).isEqualTo("WORKSPACE");
        assertThat(snapshot.path("modelId").asLong())
                .isEqualTo(llmModels
                        .findByWorkspaceIdAndSlug(workspace.getId(), "review-model")
                        .orElseThrow()
                        .getId());
        return job;
    }

    @ParameterizedTest(name = "issue {0} stays on its source provider")
    @ValueSource(strings = {"updated", "opened"})
    void shouldRecordIssueOccasionOnlyForItsProviderWhenForeignWorkspaceMonitorsSamePath(String transition) {
        boolean updated = transition.equals("updated");
        SignalName signal = updated ? ScmSignals.ISSUE_UPDATED : ScmSignals.ISSUE_OPENED;
        IssueIdentityFixture fixture = issueIdentityFixture(signal);
        IssueAgentJobEventListener listener =
                AopTestUtils.getUltimateTargetObject(beanFactory.createBean(IssueAgentJobEventListener.class));
        var dispatch = new TransactionTemplate(Objects.requireNonNull(transactions.getTransactionManager()));
        dispatch.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        if (updated) {
            var event = new ScmDomainEvent.IssueUpdated(fixture.issue(), Set.of("body"), fixture.context());
            dispatch.executeWithoutResult(status ->
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void beforeCommit(boolean readOnly) {
                            listener.onIssueUpdated(event);
                        }
                    }));
        } else {
            var event = new ScmDomainEvent.IssueCreated(fixture.issue(), fixture.context());
            dispatch.executeWithoutResult(status -> listener.onIssueCreated(event));
        }

        String revision = updated
                ? beanFactory
                        .getBean(IssueEvidenceRevision.class)
                        .of(fixture.issue())
                        .value()
                : ScmSignals.issueOpenedKey(
                                fixture.own().getId(),
                                fixture.issue().id(),
                                fixture.issue().title(),
                                fixture.issue().body())
                        .orElseThrow()
                        .revision()
                        .value();
        assertThat(signals.findForArtifact(
                        fixture.own().getId(),
                        ScmSignals.ISSUE.value(),
                        fixture.issue().id()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getWorkspace().getId())
                            .isEqualTo(fixture.own().getId());
                    assertThat(row.getArtifactKind()).isEqualTo(ScmSignals.ISSUE.value());
                    assertThat(row.getArtifactId()).isEqualTo(fixture.issue().id());
                    assertThat(row.getSignalName()).isEqualTo(signal.value());
                    assertThat(row.getRevision()).isEqualTo(revision);
                    assertThat(row.getDiscoveredVia()).isEqualTo(DiscoveredVia.EVENT);
                    assertThat(row.getState()).isEqualTo(updated ? SignalState.DEFERRED : SignalState.TRIGGERED);
                    if (updated) {
                        assertThat(row.getJobId()).isNull();
                    } else {
                        assertThat(row.getStateReason()).isNull();
                        AgentJob job = jobs.findByIdAndWorkspaceId(
                                        Objects.requireNonNull(row.getJobId()),
                                        fixture.own().getId())
                                .orElseThrow();
                        assertThat(job.getWorkspace().getId())
                                .isEqualTo(fixture.own().getId());
                        assertThat(job.getJobType()).isEqualTo(AgentJobType.ISSUE_REVIEW);
                        assertThat(job.getArtifactKind()).isEqualTo(ScmSignals.ISSUE);
                        assertThat(job.getIntegrationKind()).isEqualTo(IntegrationKind.GITHUB);
                        assertThat(job.getPracticeTriggerMode()).isEqualTo(TriggerMode.AUTO);
                        var metadata = Objects.requireNonNull(job.getMetadata());
                        assertThat(metadata.path("issue_id").asLong())
                                .isEqualTo(fixture.issue().id());
                        assertThat(metadata.path("repository_id").asLong())
                                .isEqualTo(fixture.issue().repository().id());
                        assertThat(metadata.path(AgentJob.SIGNAL_REVISION_METADATA_KEY)
                                        .asString())
                                .isEqualTo(revision);
                    }
                });
        assertThat(signals.findForArtifact(
                        fixture.foreign().getId(),
                        ScmSignals.ISSUE.value(),
                        fixture.issue().id()))
                .as("a same-path monitor on another provider must not receive the GitHub occasion")
                .isEmpty();
    }

    private IssueIdentityFixture issueIdentityFixture(SignalName signal) {
        var owner = persistUser("issue-identity-owner");
        Workspace own =
                createWorkspace("issue-events-own", "GitHub workspace", "issue-events-own", AccountType.ORG, owner);
        Workspace foreign = createWorkspace(
                "issue-events-foreign", "GitLab workspace", "issue-events-foreign", AccountType.ORG, owner);
        var github = ensureGitHubProvider();
        var gitlab = ensureGitLabProvider();
        connectActivitySource(own, github);
        connectActivitySource(foreign, gitlab);
        Repository source = null;
        for (Workspace workspace : new Workspace[] {own, foreign}) {
            var provider = workspace.getId().equals(own.getId()) ? github : gitlab;
            var repository = new Repository();
            repository.setProvider(provider);
            repository.setNativeId(97501L);
            repository.setName("shared");
            repository.setNameWithOwner("issue-events-org/shared");
            repository.setHtmlUrl(provider.getServerUrl() + "/issue-events-org/shared");
            repository.setDefaultBranch("main");
            repository = repositories.saveAndFlush(repository);
            var monitor = new RepositoryToMonitor();
            monitor.setWorkspace(workspace);
            monitor.setNameWithOwner(repository.getNameWithOwner());
            monitor.setNativeId(repository.getNativeId());
            monitors.saveAndFlush(monitor);
            if (workspace.getId().equals(own.getId())) source = repository;
        }
        assertThat(resolver.resolveAllForRepository("issue-events-org/shared"))
                .extracting(Workspace::getId)
                .containsExactly(own.getId(), foreign.getId());
        var author = persistUser("testuser");
        ensureWorkspaceMembership(own, author, WorkspaceRole.MEMBER);
        assertThat(workspaceMembershipService.isPracticeReviewEligible(own.getId(), author.getId()))
                .isTrue();
        makeReviewable(own, true, signal);
        Repository repository = Objects.requireNonNull(source);
        var issue = new Issue();
        issue.setProvider(github);
        issue.setNativeId(97601L);
        issue.setRepository(repository);
        issue.setAuthor(author);
        issue.setNumber(78);
        issue.setTitle("Describe the issue on GitHub");
        issue.setBody("This request describes the work and its expected result.");
        issue.setHtmlUrl(repository.getHtmlUrl() + "/issues/78");
        issue.setState(Issue.State.OPEN);
        issue.setCreatedAt(Instant.now());
        issue.setUpdatedAt(Instant.now());
        issue = issues.saveAndFlush(issue);
        var data = ScmEventPayload.IssueData.from(issue);
        var context = new EventContext(
                UUID.randomUUID(),
                Instant.now(),
                own.getId(),
                new RepositoryRef(repository.getId(), repository.getNameWithOwner(), "main"),
                DataSource.WEBHOOK,
                "issue",
                UUID.randomUUID().toString(),
                null,
                author.getId());
        return new IssueIdentityFixture(own, foreign, data, context);
    }

    private record IssueIdentityFixture(
            Workspace own, Workspace foreign, ScmEventPayload.IssueData issue, EventContext context) {}

    private void makeReviewable(Workspace workspace, boolean automaticEnabled) {
        makeReviewable(workspace, automaticEnabled, ScmSignals.PULL_REQUEST_OPENED);
    }

    private void makeReviewable(Workspace workspace, boolean automaticEnabled, SignalName signal) {
        Workspace stored = workspaces.findById(workspace.getId()).orElseThrow();
        stored.getFeatures().setPracticesEnabled(true);
        stored.getFeatures().setPracticeReviewAutoTriggerEnabled(automaticEnabled);
        workspaces.saveAndFlush(stored);
        var practice = new Practice();
        practice.setWorkspace(stored);
        practice.setSlug("describes-the-work");
        practice.setName("Describes the work");
        practice.setCriteria("The description says what changed and why.");
        practice.setAutomatedReviewPolicy(PracticeTestEvidence.forArtifact(signal.artifactKind()));
        PracticeTestEvidence.configure(practice, signal);
        practice.setAutonomy(PracticeAutonomy.AUTOMATIC);
        practices.saveAndFlush(practice);
        var connection = new WorkspaceLlmConnection();
        connection.setWorkspace(stored);
        connection.setSlug("review-connection");
        connection.setDisplayName("Review connection");
        connection.setBaseUrl("https://api.openai.com");
        connection.setApiProtocol("openai-completions");
        connection.setEnabled(true);
        connection = llmConnections.saveAndFlush(connection);
        var model = new WorkspaceLlmModel();
        model.setWorkspace(stored);
        model.setConnection(connection);
        model.setSlug("review-model");
        model.setDisplayName("Review model");
        model.setUpstreamModelId("gpt-5");
        model.setEnabled(true);
        model = llmModels.saveAndFlush(model);
        var binding = new WorkspaceAgentBinding();
        binding.setWorkspace(stored);
        binding.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        binding.setEnabled(true);
        binding.setWorkspaceModel(model);
        binding.setTimeoutSeconds(300);
        bindings.saveAndFlush(binding);
    }
}
