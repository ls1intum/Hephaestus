package de.tum.cit.aet.hephaestus.agent.job;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPrice;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPriceRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.PricingMode;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.agent.handler.AdmittedResultFixtures;
import de.tum.cit.aet.hephaestus.agent.handler.ResultAdmissionService;
import de.tum.cit.aet.hephaestus.agent.handler.RuntimePracticeTestData;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.core.llm.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsagePricingState;
import de.tum.cit.aet.hephaestus.core.privacy.spi.PersonCopyIdentity;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProvider;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderRepository;
import de.tum.cit.aet.hephaestus.integration.core.connection.IdentityProviderType;
import de.tum.cit.aet.hephaestus.integration.core.spi.ItemKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.UserRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeVersionService;
import de.tum.cit.aet.hephaestus.practices.model.Practice;
import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.result.ResultRepository;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackMode;
import de.tum.cit.aet.hephaestus.practices.spi.ResultOutcome;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewJobStatus;
import de.tum.cit.aet.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.cit.aet.hephaestus.testconfig.LlmCatalogTestFixtures;
import de.tum.cit.aet.hephaestus.testconfig.TestEntities;
import de.tum.cit.aet.hephaestus.testconfig.TestUserFactory;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Verifies orphan recovery, retry fencing, and claim eligibility against PostgreSQL. */
@DisplayName("Orphan recovery over PostgreSQL Integration")
class AgentOrphanRecoveryIntegrationTest extends BaseIntegrationTest {
    @Autowired
    private PracticeVersionService runtimePracticeVersions;

    private ReviewJobZombieSweeper sweeper;

    @Autowired
    private LlmUsageRecorder usageRecorder;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ReviewJobRepository jobRepository;

    @Autowired
    private WorkerRegistryRepository workerRegistryRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceAgentBindingRepository agentBindingRepository;

    @Autowired
    private LlmConnectionRepository connectionRepository;

    @Autowired
    private LlmModelRepository modelRepository;

    @Autowired
    private LlmModelPriceRepository priceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PracticeRepository practiceRepository;

    @Autowired
    private PracticeVersionRepository practiceRevisionRepository;

    @Autowired
    private ResultRepository practiceResultRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdentityProviderRepository identityProviderRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ReviewCompositionContinuation continuation;

    private Workspace workspace;
    private LlmModel instanceModel;
    private Practice practice;
    private User developer;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        AgentProperties properties = mock(AgentProperties.class);
        when(properties.maxRetries()).thenReturn(5);
        sweeper = new ReviewJobZombieSweeper(
                jobRepository,
                workerRegistryRepository,
                properties,
                objectMapper,
                transactionTemplate,
                usageRecorder,
                meterRegistry,
                new ReviewJobTelemetry(meterRegistry, Tracer.NOOP));
        sweeper.setCompositionContinuation(continuation);
        workspace = workspaceRepository.save(TestEntities.activeWorkspace("orphan-recovery-ws"));

        LlmConnection connection = connectionRepository.save(LlmCatalogTestFixtures.connection("orphan-recovery"));
        instanceModel =
                modelRepository.save(LlmCatalogTestFixtures.model(connection, "orphan-recovery-model", "test-model"));

        LlmModelPrice price = new LlmModelPrice();
        price.setModel(instanceModel);
        price.setPricingMode(PricingMode.NO_CHARGE);
        price.setNote("Integration-test model has no per-token charge");
        price.setEffectiveFrom(Instant.now().minusSeconds(60));
        priceRepository.save(price);

        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setWorkspace(workspace);
        binding.setPurpose(AgentPurpose.PRACTICE_REVIEW);
        binding.setEnabled(true);
        binding.setInstanceModel(instanceModel);
        agentBindingRepository.save(binding);

        practice = runtimePracticeVersions.create(new PracticeVersionService.NewPractice(
                workspace,
                "orphan-recovery-practice",
                new ItemKind("scm.pull_request"),
                RuntimePracticeTestData.definition(
                        new ItemKind("scm.pull_request"), "Orphan recovery practice", "The first definition", null),
                null,
                0,
                null));
        practice.setFeedbackMode(FeedbackMode.AUTOMATIC);
        IdentityProvider provider = identityProviderRepository
                .findByTypeAndServerUrl(IdentityProviderType.GITHUB, "https://github.com")
                .orElseGet(() -> identityProviderRepository.save(
                        new IdentityProvider(IdentityProviderType.GITHUB, "https://github.com")));
        developer = userRepository.save(TestUserFactory.createUser(100L, "developer", provider));
    }

    @Test
    @DisplayName("a dead worker's RUNNING job is requeued (attempt++) and becomes claimable again")
    void orphanRecoveryRequeuesAndBecomesClaimable() {
        UUID jobId = runningJobOwnedBy("dead-replica", Instant.now().minus(Duration.ofMinutes(5)), 0);
        registerStaleWorker("dead-replica");

        sweeper.recoverOrphanedJobs();

        ReviewJob requeued = jobRepository.findById(jobId).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(ReviewJobStatus.QUEUED);
        assertThat(requeued.getWorkerId()).isNull();
        assertThat(requeued.getAttempt()).isEqualTo(1);

        fastForwardAvailableAt(jobId);

        assertThat(eligibleForClaim(jobId)).isTrue();
    }

    @Test
    @DisplayName("orphan requeue rotates the job token — the old token no longer authenticates, the new one does")
    void orphanRequeueRotatesTheJobToken() {
        UUID jobId = runningJobOwnedBy("dead-replica", Instant.now().minus(Duration.ofMinutes(5)), 0);
        registerStaleWorker("dead-replica");
        ReviewJob before = jobRepository.findById(jobId).orElseThrow();
        String oldTokenHash = before.getJobTokenHash();

        sweeper.recoverOrphanedJobs();

        ReviewJob requeued = jobRepository.findById(jobId).orElseThrow();
        String newTokenHash = requeued.getJobTokenHash();
        assertThat(newTokenHash).isNotEqualTo(oldTokenHash);

        // Credentials for the previous attempt cannot authorize the requeued execution.
        assertThat(jobRepository.findById(jobId).orElseThrow().getAttempt()).isEqualTo(before.getAttempt() + 1);

        fastForwardAvailableAt(jobId);

        assertThat(eligibleForClaim(jobId)).isTrue();
        assertThat(jobRepository.findById(jobId).orElseThrow().getAttempt()).isEqualTo(before.getAttempt() + 1);
    }

    @Test
    @DisplayName("a direct claim attempt made WHILE still backed off does not succeed")
    void claimAttemptWhileStillBackedOffDoesNotSucceed() {
        UUID jobId = runningJobOwnedBy("dead-replica-5", Instant.now().minus(Duration.ofMinutes(5)), 0);
        registerStaleWorker("dead-replica-5");

        sweeper.recoverOrphanedJobs();

        ReviewJob requeued = jobRepository.findById(jobId).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(ReviewJobStatus.QUEUED);
        assertThat(requeued.getAvailableAt())
                .as("the backoff-computed available_at is still in the future")
                .isAfter(Instant.now());

        assertThat(eligibleForClaim(jobId)).isFalse();
        ReviewJob stillQueued = jobRepository.findById(jobId).orElseThrow();
        assertThat(stillQueued.getStatus()).isEqualTo(ReviewJobStatus.QUEUED);
    }

    @Test
    @DisplayName("a job whose available_at is in the future is not offered as a poll candidate")
    void jobWithFutureAvailableAtIsNotClaimed() {
        UUID jobId = runningJobOwnedBy("dead-replica-4", Instant.now().minus(Duration.ofMinutes(5)), 0);
        registerStaleWorker("dead-replica-4");

        sweeper.recoverOrphanedJobs();

        // The requeue's backoff puts available_at far enough out that this is not a race with the clock.
        ReviewJob requeued = jobRepository.findById(jobId).orElseThrow();
        assertThat(requeued.getStatus()).isEqualTo(ReviewJobStatus.QUEUED);
        assertThat(requeued.getAvailableAt()).isAfter(Instant.now());

        assertThat(jobRepository.findQueuedIdsOldestFirst(10))
                .as("a not-yet-eligible QUEUED job must not be offered as a poll candidate")
                .doesNotContain(jobId);
    }

    @Test
    @DisplayName("an orphan already at the retry cap is failed, not requeued again")
    void orphanPastRetryCapIsFailedNotRequeued() {
        UUID jobId = runningJobOwnedBy("dead-replica-2", Instant.now().minus(Duration.ofMinutes(5)), 5);
        registerStaleWorker("dead-replica-2");

        sweeper.recoverOrphanedJobs();

        ReviewJob failed = jobRepository.findById(jobId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ReviewJobStatus.FAILED);
        assertThat(failed.getErrorMessage()).contains("retry limit is reached");
        assertThat(jobRepository.findQueuedIdsOldestFirst(10)).doesNotContain(jobId);
    }

    @Test
    @DisplayName(
            "requeueOrphan is fenced on worker_id — a stale caller cannot steal a job a live sibling has re-claimed")
    void requeueOrphanDoesNotStealAJobReclaimedBySomeoneElse() {
        UUID jobId = runningJobOwnedBy("live-sibling", Instant.now(), 0);

        // @Modifying queries need an active transaction (the sweeper normally provides one via
        // TransactionTemplate); wrap here too.
        String candidateNewToken = ReviewJob.generateJobToken();
        int updated = transactionTemplate.execute(s -> jobRepository.requeueOrphan(
                jobId, "dead-replica", 5, Instant.now(), ReviewJob.computeTokenHash(candidateNewToken)));

        assertThat(updated)
                .as("the CAS must not match — the row's worker_id does not match the stale caller's")
                .isZero();
        ReviewJob untouched = jobRepository.findById(jobId).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(ReviewJobStatus.RUNNING);
        assertThat(untouched.getWorkerId()).isEqualTo("live-sibling");
        assertThat(untouched.getAttempt()).isZero();
    }

    @Test
    @DisplayName("requeueOrphan enforces the retry cap in SQL even if a caller forgets to check it first")
    void requeueOrphanRefusesPastTheRetryCapEvenUnchecked() {
        UUID jobId = runningJobOwnedBy("dead-replica-3", Instant.now(), 5);

        String candidateNewToken = ReviewJob.generateJobToken();
        int updated = transactionTemplate.execute(s -> jobRepository.requeueOrphan(
                jobId, "dead-replica-3", 5, Instant.now(), ReviewJob.computeTokenHash(candidateNewToken)));

        assertThat(updated).isZero();
        ReviewJob unchanged = jobRepository.findById(jobId).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(ReviewJobStatus.RUNNING);
        assertThat(unchanged.getAttempt()).isEqualTo(5);
    }

    @Test
    @DisplayName("an orphan whose results were admitted fails with them kept, after its practice changed")
    void shouldFailWithTheAdmittedResultsKeptWhenTheOwningWorkerIsLostAfterAdmission() {
        UUID jobId = runningJobOwnedBy("dead-replica-6", Instant.now().minus(Duration.ofMinutes(5)), 0);
        Result observed = admitWithResult(jobId);
        changePracticeDefinition();
        registerStaleWorker("dead-replica-6");
        ReviewJob before = jobRepository.findById(jobId).orElseThrow();

        sweeper.recoverOrphanedJobs();

        ReviewJob failed = jobRepository.findById(jobId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ReviewJobStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo(ResultAdmissionService.INTERRUPTED_AFTER_ADMISSION);
        assertThat(failed.getAttempt()).isZero();
        assertThat(failed.getJobTokenHash()).isEqualTo(before.getJobTokenHash());
        assertThat(failed.getInput()).isEqualTo(before.getInput());
        assertThat(failed.getCapture()).isEqualTo(before.getCapture());
        Result kept = practiceResultRepository.findById(observed.getId()).orElseThrow();
        assertThat(kept.getReviewId()).isEqualTo(before.getReviewId());
        Long originalRevisionId = requireNonNull(observed.getPracticeVersionId());
        assertThat(kept.getPracticeVersionId()).isEqualTo(originalRevisionId);
        assertThat(practiceRevisionRepository
                        .findById(originalRevisionId)
                        .orElseThrow()
                        .getDefinition()
                        .path("criteria")
                        .asString())
                .isEqualTo("The first definition");
        assertThat(practiceRepository
                        .findById(practice.getId())
                        .orElseThrow()
                        .getCurrentVersion()
                        .getDefinition()
                        .path("criteria")
                        .asString())
                .isEqualTo("The changed definition");
        assertThat(kept.getEvidence()).isEqualTo(observed.getEvidence());
        assertThat(jobRepository.findQueuedIdsOldestFirst(10)).doesNotContain(jobId);
    }

    @Test
    @DisplayName("requeueOrphan refuses an admitted attempt in SQL, and only its owner's attempt can end it")
    void shouldRefuseToRequeueAnAdmittedAttemptWhenAnyCallerTries() {
        UUID admitted = runningJobOwnedBy("owner", Instant.now(), 0);
        admitWithResult(admitted);
        UUID unadmitted = runningJobOwnedBy("owner", Instant.now(), 0);

        String token = ReviewJob.generateJobToken();
        int requeued = transactionTemplate.execute(s ->
                jobRepository.requeueOrphan(admitted, "owner", 5, Instant.now(), ReviewJob.computeTokenHash(token)));
        int byStranger = transactionTemplate.execute(
                s -> jobRepository.failAdmittedOwnedBy(admitted, "stranger", 0, Instant.now(), "lost"));
        int byLaterAttempt = transactionTemplate.execute(
                s -> jobRepository.failAdmittedOwnedBy(admitted, "owner", 1, Instant.now(), "lost"));
        int notAdmitted = transactionTemplate.execute(
                s -> jobRepository.failAdmittedOwnedBy(unadmitted, "owner", 0, Instant.now(), "lost"));

        assertThat(requeued).isZero();
        assertThat(byStranger).isZero();
        assertThat(byLaterAttempt).isZero();
        assertThat(notAdmitted).isZero();
        ReviewJob untouched = jobRepository.findById(admitted).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(ReviewJobStatus.RUNNING);
        assertThat(untouched.getAttempt()).isZero();
        assertThat(jobRepository.findById(unadmitted).orElseThrow().getStatus()).isEqualTo(ReviewJobStatus.RUNNING);
    }

    @Test
    @DisplayName(
            "a lost admitted review whose original inputs are vouched for continues as one writer of its admission")
    void shouldContinueALostAdmittedReviewUnderItsOriginalAdmissionAsANewAttempt() {
        UUID jobId = runningJobOwnedBy("dead-replica-9", Instant.now().minus(Duration.ofMinutes(5)), 0);
        Result observed = admitWithResult(jobId);
        keepOriginalInputs(jobId, 0, "READY", "COMPLETE", Instant.now().plus(Duration.ofHours(1)));
        spentBeforeItWasLost(jobId, 3);
        registerStaleWorker("dead-replica-9");
        ReviewJob before = jobRepository.findById(jobId).orElseThrow();

        sweeper.recoverOrphanedJobs();

        ReviewJob continuing = jobRepository.findById(jobId).orElseThrow();
        assertThat(continuing.getStatus()).isEqualTo(ReviewJobStatus.QUEUED);
        assertThat(continuing.getAttempt()).isEqualTo(1);
        assertThat(continuing.getJobTokenHash()).isNotEqualTo(before.getJobTokenHash());
        // The admission and the evidence it was verified against are the original attempt's, untouched.
        assertThat(continuing.getInput()).isEqualTo(before.getInput());
        assertThat(continuing.getCapture()).isEqualTo(before.getCapture());
        assertThat(ResultAdmissionService.isContinuation(continuing)).isTrue();
        assertThat(ResultAdmissionService.citationAttempt(continuing)).isZero();
        assertThat(practiceResultRepository.findById(observed.getId())).isPresent();
        // The lost attempt is settled as itself; the new attempt starts from nothing.
        assertThat(continuing.getLlmCalls()).isZero();
        assertThat(jdbc.queryForList(
                        "SELECT total_calls FROM llm_usage_event WHERE source_id::text = ? AND source_attempt = 0",
                        Integer.class,
                        jobId.toString()))
                .containsExactly(3);
        // The lost worker's own attempt is gone: its requeue or its failure matches nothing now.
        String token = ReviewJob.generateJobToken();
        Integer staleRequeue = transactionTemplate.execute(s -> {
            continuation.holdRecoveryCustody();
            return continuation.requeue(
                    continuing,
                    "dead-replica-9",
                    new ReviewJobRepository.CompositionRequeue(
                            0, 5, Instant.now(), Instant.now(), token, ReviewJob.computeTokenHash(token)));
        });
        assertThat(staleRequeue).isZero();
        Integer staleFailure = transactionTemplate.execute(
                s -> jobRepository.failAdmittedOwnedBy(jobId, "dead-replica-9", 0, Instant.now(), "lost"));
        assertThat(staleFailure).isZero();
        fastForwardAvailableAt(jobId);
        assertThat(eligibleForClaim(jobId)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "erased receipt",
                "receipt not indexing its copies",
                "expired inputs",
                "legacy admission",
                "suppressed person"
            })
    @DisplayName("a lost admitted review whose original inputs cannot be vouched for fails as before")
    void shouldFailTheLostAdmittedReviewWhenItsOriginalInputsCannotBeContinued(String why) {
        UUID jobId = runningJobOwnedBy("dead-replica-10", Instant.now().minus(Duration.ofMinutes(5)), 0);
        admitWithResult(jobId);
        Instant fresh = Instant.now().plus(Duration.ofHours(1));
        switch (why) {
            case "erased receipt" -> keepOriginalInputs(jobId, 0, "ERASE_REQUESTED", "COMPLETE", fresh);
            case "suppressed person" -> {
                keepOriginalInputs(jobId, 0, "READY", "COMPLETE", fresh);
                jdbc.update(
                        "INSERT INTO person_suppression(id,provider_id,subject,team_key) VALUES (?,?,?,'')",
                        UUID.randomUUID(),
                        requireNonNull(developer.getProvider()).getId(),
                        "100");
            }
            case "receipt not indexing its copies" -> keepOriginalInputs(jobId, 0, "READY", "UNKNOWN", fresh);
            case "expired inputs" ->
                keepOriginalInputs(jobId, 0, "READY", "COMPLETE", Instant.now().minusSeconds(1));
            default -> {
                keepOriginalInputs(jobId, 0, "READY", "COMPLETE", fresh);
                // Admitted before the admitting attempt was recorded: nothing says whose citations these were.
                jdbc.update("UPDATE review_job SET admission_attempt = NULL WHERE id = ?", jobId);
            }
        }
        spentBeforeItWasLost(jobId, 2);
        registerStaleWorker("dead-replica-10");
        ReviewJob before = jobRepository.findById(jobId).orElseThrow();

        sweeper.recoverOrphanedJobs();

        ReviewJob failed = jobRepository.findById(jobId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(ReviewJobStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo(ResultAdmissionService.INTERRUPTED_AFTER_ADMISSION);
        assertThat(failed.getAttempt()).isZero();
        assertThat(failed.getJobTokenHash()).isEqualTo(before.getJobTokenHash());
        assertThat(jdbc.queryForList(
                        "SELECT total_calls FROM llm_usage_event WHERE source_id::text = ? AND source_attempt = 0",
                        Integer.class,
                        jobId.toString()))
                .containsExactly(2);
    }

    @ParameterizedTest(name = "admission committed: {0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("recovery waiting on the admission's row lock decides on what the admission left behind")
    void shouldDecideOnTheCommittedAdmissionWhenRecoveryWaitsForItsRowLock(boolean committed) throws Exception {
        UUID jobId = runningJobOwnedBy("dead-replica-7", Instant.now().minus(Duration.ofMinutes(5)), 0);
        registerStaleWorker("dead-replica-7");

        whileRecoveryWaits(jobId, committed, job -> {
            job.setAdmissionDigest("admitted-digest");
            job.setAdmissionAttempt(job.getAttempt());
        });

        ReviewJob after = jobRepository.findById(jobId).orElseThrow();
        if (committed) {
            assertThat(after.getStatus()).isEqualTo(ReviewJobStatus.FAILED);
            assertThat(after.getAttempt()).isZero();
            assertThat(ResultAdmissionService.isAdmitted(after)).isTrue();
        } else {
            assertThat(after.getStatus()).isEqualTo(ReviewJobStatus.QUEUED);
            assertThat(after.getAttempt()).isEqualTo(1);
            assertThat(ResultAdmissionService.isAdmitted(after)).isFalse();
        }
    }

    @Test
    @DisplayName("recovery leaves alone an attempt a new claim took while it waited for the row lock")
    void shouldLeaveTheNewClaimAloneWhenTheOrphanWasReclaimedWhileRecoveryWaited() throws Exception {
        UUID jobId = runningJobOwnedBy("dead-replica-8", Instant.now().minus(Duration.ofMinutes(5)), 5);
        registerStaleWorker("dead-replica-8");

        whileRecoveryWaits(jobId, true, job -> {
            job.setWorkerId("live-sibling");
            job.setAttempt(6);
        });

        ReviewJob after = jobRepository.findById(jobId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(ReviewJobStatus.RUNNING);
        assertThat(after.getWorkerId()).isEqualTo("live-sibling");
        assertThat(after.getAttempt()).isEqualTo(6);
        assertThat(after.getErrorMessage()).isNull();
    }

    /**
     * Holds the job's row lock as the admission does, changes it, and lets the sweep run against it: the
     * sweep blocks on the lock, then decides on what this transaction committed or rolled back.
     */
    private void whileRecoveryWaits(UUID jobId, boolean commit, Consumer<ReviewJob> change) throws Exception {
        var locked = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var holder = pool.submit(() -> transactionTemplate.executeWithoutResult(tx -> {
                ReviewJob job = jobRepository.lockById(jobId).orElseThrow();
                change.accept(job);
                jobRepository.saveAndFlush(RuntimeJobTestData.persistParent(jdbc, job));
                locked.countDown();
                awaitBlockedTransaction();
                if (!commit) tx.setRollbackOnly();
            }));
            var recovery = pool.submit(() -> {
                assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();
                sweeper.recoverOrphanedJobs();
                return null;
            });
            holder.get(30, TimeUnit.SECONDS);
            recovery.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitBlockedTransaction() {
        Integer holderPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
        Awaitility.await().pollInSameThread().atMost(30, TimeUnit.SECONDS).until(() -> {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            return Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT FROM pg_stat_activity WHERE CAST(? AS integer) ="
                            + " ANY(pg_blocking_pids(pid)))",
                    Boolean.class,
                    holderPid));
        });
    }

    /**
     * An admission as it is persisted: a result of a practice revision, citing this job's capture,
     * and the digest on the job's metadata.
     */
    private Result admitWithResult(UUID jobId) {
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            ReviewJob job = jobRepository.findById(jobId).orElseThrow();
            UUID id = UUID.randomUUID();
            var row = Result.builder()
                    .id(id)
                    .workspaceId(job.getWorkspaceId())
                    .reviewId(job.getReviewId())
                    .practiceId(practice.getId())
                    .practiceVersionId(practice.getCurrentVersion().getId())
                    .itemId(job.getInput().itemId())
                    .itemVersion("fixture:one")
                    .developerId(null)
                    .outcome(ResultOutcome.MET)
                    .summary("Stored result")
                    .explanation("The recorded quote supports it.")
                    .evidence(AdmittedResultFixtures.evidence("scm.pull-request.core"))
                    .coverage(objectMapper.readTree(
                            "{\"source\":\"claimed\",\"complete\":true,\"selected\":1,\"checked\":1,\"total\":1}"))
                    .recordedAt(Instant.now())
                    .build();
            practiceResultRepository.saveAndFlush(row);
            job.setAdmissionDigest("admitted-digest");
            job.setAdmissionAttempt(job.getAttempt());
            job.setCapture(objectMapper
                    .createObjectNode()
                    .set("manifest", objectMapper.createObjectNode().put("contractVersion", "2.0.0")));
            jobRepository.saveAndFlush(job);
            return row;
        }));
    }

    /**
     * What an admission keeps for a later writer, as persisted: the inputs under its digest and attempt, and the
     * capture receipt that vouches for whose rows they copied.
     */
    private void keepOriginalInputs(
            UUID jobId, int attempt, String receiptState, String dependencies, Instant expiresAt) {
        UUID copyId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO person_evidence_copy(id,workspace_id,job_id,store_id,state,payload) "
                        + "VALUES (?,?,?,?,?,CAST(? AS jsonb))",
                copyId,
                workspace.getId(),
                jobId,
                UUID.randomUUID(),
                receiptState,
                objectMapper
                        .createObjectNode()
                        .put("attempt", attempt)
                        .put("dependencies", dependencies)
                        .set(
                                "identities",
                                objectMapper.valueToTree(
                                        List.of(new PersonCopyIdentity("GITHUB", "https://github.com", "100", null))))
                        .toString());
        jdbc.update(
                "INSERT INTO review_composition_basis(job_id,workspace_id,admission_attempt,admission_digest,"
                        + "basis_version,basis_digest,copy_id,byte_size,created_at,expires_at,payload) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb))",
                jobId,
                workspace.getId(),
                attempt,
                "admitted-digest",
                ReviewCompositionBasis.VERSION,
                ReviewCompositionBasis.digest(Map.of()),
                copyId,
                12,
                Timestamp.from(expiresAt.minus(Duration.ofHours(2))),
                Timestamp.from(expiresAt),
                "{\"files\":{}}");
    }

    /** The attempt ran and the proxy counted its calls before its worker was lost. */
    private void spentBeforeItWasLost(UUID jobId, int calls) {
        jdbc.update("UPDATE review_job SET execution_started_at = now(), llm_calls = ? WHERE id = ?", calls, jobId);
    }

    /** The practice's definition moves on after the admission, as a later edit would. */
    private void changePracticeDefinition() {
        var kind = practice.getKind();
        runtimePracticeVersions.append(
                practice,
                RuntimePracticeTestData.definition(kind, "Updated standard", "The second definition", null),
                null);
    }

    private UUID runningJobOwnedBy(String workerId, Instant startedAt, int attempt) {
        ReviewJob job = new ReviewJob();
        RuntimeJobTestData.initialize(job, workspace.getId());

        job.setStatus(ReviewJobStatus.RUNNING);
        job.setConfigSnapshot(new ConfigSnapshot(
                        ConfigSnapshot.SCHEMA_VERSION,
                        "openai-completions",
                        "https://api.openai.com/v1",
                        "test-model",
                        null,
                        null,
                        null,
                        UsageFundingSource.INSTANCE,
                        instanceModel.getConnection().getId(),
                        instanceModel.getId(),
                        workspace.getId(),
                        600,
                        false,
                        null,
                        null,
                        null)
                .withPriceSnapshot(new LlmPriceSnapshot(
                        UsageFundingSource.INSTANCE, UsagePricingState.NO_CHARGE, null, null, null, null, null, null))
                .toJson(objectMapper));
        job.setWorkerId(workerId);
        job.setStartedAt(startedAt);
        job.setAttempt(attempt);
        return jobRepository
                .saveAndFlush(RuntimeJobTestData.persistParent(jdbc, job))
                .getId();
    }

    private void registerStaleWorker(String workerId) {
        // The orphan query judges the lease on the database clock.
        Instant lastHeartbeat = requireNonNull(jdbc.queryForObject(
                        "SELECT now() - make_interval(secs => ?)",
                        OffsetDateTime.class,
                        AgentProperties.WORKER_LEASE_TTL.plusMinutes(1).toSeconds()))
                .toInstant();
        WorkerRegistry w = new WorkerRegistry();
        w.setWorkerId(workerId);
        w.setLastHeartbeat(lastHeartbeat);
        w.setRegisteredAt(lastHeartbeat);
        workerRegistryRepository.saveAndFlush(w);
    }

    private boolean eligibleForClaim(UUID jobId) {
        return transactionTemplate.execute(status -> jobRepository
                .findByIdQueuedForUpdateSkipLocked(jobId, Instant.now())
                .isPresent());
    }

    private void fastForwardAvailableAt(UUID jobId) {
        transactionTemplate.executeWithoutResult(status -> {
            ReviewJob job = jobRepository.findById(jobId).orElseThrow();
            job.setAvailableAt(Instant.now().minus(Duration.ofSeconds(1)));
            jobRepository.saveAndFlush(RuntimeJobTestData.persistParent(jdbc, job));
        });
    }
}
