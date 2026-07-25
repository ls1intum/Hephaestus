package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelVisibility;
import de.tum.cit.aet.hephaestus.agent.config.AgentPurpose;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBinding;
import de.tum.cit.aet.hephaestus.agent.config.WorkspaceAgentBindingRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobService;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/** Unified LLM ledger persistence and budget enforcement against the real database. */
@Tag("integration")
class LlmUsageLedgerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private LlmUsageRecorder recorder;

    @Autowired
    private LlmUsageEventRepository usageRepository;

    @Autowired
    private LlmBudgetService budgetService;

    @Autowired
    private AgentJobService agentJobService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AgentJobRepository jobRepository;

    @Autowired
    private WorkspaceAgentBindingRepository bindingRepository;

    @Autowired
    private LlmConnectionRepository llmConnectionRepository;

    @Autowired
    private LlmModelRepository llmModelRepository;

    @Autowired
    private LlmUsageService llmUsageService;

    @Autowired
    private LlmUsageAdminService llmUsageAdminService;

    private Workspace setupWorkspace(String slug) {
        User owner = persistUser(slug + "-owner");
        return createWorkspace(slug, "Usage " + slug, slug + "-org", AccountType.ORG, owner);
    }

    private LlmUsageRecorder.LlmUsageSample sample(
        LlmUsageJobType jobType,
        LlmUsageSourceType sourceType,
        UUID sourceId,
        int sourceAttempt,
        String model,
        long inputTokens,
        long outputTokens,
        LlmPriceSnapshot price
    ) {
        return new LlmUsageRecorder.LlmUsageSample(
            jobType,
            sourceType,
            sourceId,
            sourceAttempt,
            model,
            inputTokens,
            outputTokens,
            0,
            0,
            0,
            1,
            price,
            Instant.now()
        );
    }

    private LlmUsageRecorder.LlmUsageSample agentSample(
        UUID sourceId,
        int sourceAttempt,
        long inputTokens,
        LlmPriceSnapshot price
    ) {
        return sample(
            LlmUsageJobType.PULL_REQUEST_REVIEW,
            LlmUsageSourceType.AGENT_JOB,
            sourceId,
            sourceAttempt,
            "gpt-5",
            inputTokens,
            0,
            price
        );
    }

    private void record(Long workspaceId, LlmUsageRecorder.LlmUsageSample sample) {
        transactionTemplate.executeWithoutResult(status -> recorder.record(workspaceId, sample));
    }

    private void recordUnverifiable(Long workspaceId, LlmUsageRecorder.LlmUsageSample sample) {
        transactionTemplate.executeWithoutResult(status -> recorder.recordUnverifiable(workspaceId, sample));
    }

    private LlmPriceSnapshot pricedInstance(String perMInput, String perMOutput) {
        return new LlmPriceSnapshot(
            FundingSource.INSTANCE,
            PricingState.PRICED,
            42L,
            null,
            new BigDecimal(perMInput),
            new BigDecimal(perMOutput),
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }

    /** The own-provider mirror of {@link #pricedInstance}: the workspace's own money. */
    private LlmPriceSnapshot workspacePriced(String perMInput, String perMOutput) {
        return new LlmPriceSnapshot(
            FundingSource.WORKSPACE,
            PricingState.PRICED,
            null,
            84L,
            new BigDecimal(perMInput),
            new BigDecimal(perMOutput),
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }

    private double blockedCount(String cap) {
        return meterRegistry.counter("llm.budget.blocked", "surface", "agent_job", "cap", cap).count();
    }

    /**
     * Give the workspace a PRACTICE_DETECTION binding funded by {@code fundingSource} — submission
     * resolves it first, because the cap that applies is the one belonging to whoever pays for it.
     */
    private WorkspaceAgentBinding bindDetectionTo(Workspace workspace, FundingSource fundingSource) {
        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setWorkspace(workspace);
        binding.setPurpose(AgentPurpose.PRACTICE_DETECTION);
        binding.setEnabled(true);
        binding.setTimeoutSeconds(300);
        if (fundingSource == FundingSource.INSTANCE) {
            binding.setInstanceModel(instanceModel(workspace.getWorkspaceSlug()));
        }
        return bindingRepository.save(binding);
    }

    private LlmModel instanceModel(String slug) {
        LlmConnection connection = new LlmConnection();
        connection.setSlug(slug + "-conn");
        connection.setDisplayName("Ledger " + slug);
        connection.setBaseUrl("https://api.openai.example/v1");
        connection.setApiProtocol("openai-completions");
        connection.setEnabled(true);
        connection = llmConnectionRepository.save(connection);

        LlmModel model = new LlmModel();
        model.setConnection(connection);
        model.setSlug(slug + "-model");
        model.setDisplayName("Ledger model " + slug);
        model.setUpstreamModelId("gpt-ledger");
        model.setVisibility(ModelVisibility.PUBLIC);
        model.setEnabled(true);
        return llmModelRepository.save(model);
    }

    /** A QUEUED job whose availability was pushed out, with the given hold reason. */
    private AgentJob queuedJob(Workspace workspace, Instant availableAt, @Nullable String holdReason) {
        AgentJob job = new AgentJob();
        job.setWorkspace(workspace);
        job.setJobType(AgentJobType.PULL_REQUEST_REVIEW);
        job.setPurpose(AgentPurpose.PRACTICE_DETECTION);
        job.setStatus(AgentJobStatus.QUEUED);
        job.setConfigSnapshot(new ObjectMapper().createObjectNode());
        job.prePersist();
        job.setAvailableAt(availableAt);
        job.setHoldReason(holdReason);
        return jobRepository.saveAndFlush(job);
    }

    @Test
    void recordAppendsOneLedgerRowUsingTheAdmissionPriceSnapshot() {
        Workspace workspace = setupWorkspace("ledger-instance-priced");
        UUID sourceId = UUID.randomUUID();

        record(
            workspace.getId(),
            sample(
                LlmUsageJobType.PULL_REQUEST_REVIEW,
                LlmUsageSourceType.AGENT_JOB,
                sourceId,
                2,
                "gpt-5",
                1_000_000,
                1_000_000,
                pricedInstance("3.00", "9.00")
            )
        );

        var events = usageRepository.findByWorkspaceId(workspace.getId());
        assertThat(events).hasSize(1);
        var event = events.getFirst();
        assertThat(event.getSourceId()).isEqualTo(sourceId);
        assertThat(event.getSourceType()).isEqualTo(LlmUsageSourceType.AGENT_JOB);
        assertThat(event.getSourceAttempt()).isEqualTo(2);
        assertThat(event.getCostUsd()).isEqualByComparingTo("12.00");
        assertThat(event.getPricingState()).isEqualTo(PricingState.PRICED);
        assertThat(event.getFundingSource()).isEqualTo(FundingSource.INSTANCE);
        assertThat(event.getAppliedPriceId()).isEqualTo(42L);
        assertThat(budgetService.monthToDateCost(workspace.getId())).isEqualByComparingTo("12.00");
    }

    @Test
    void noChargeAdmissionIsRecordedAsZeroCostAndNeverAlerts() {
        Workspace workspace = setupWorkspace("ledger-instance-free");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("50.00"));
        workspaceRepository.save(workspace);
        double before = meterRegistry.counter("llm.budget.exhausted").count();
        LlmPriceSnapshot noCharge = new LlmPriceSnapshot(
            FundingSource.INSTANCE,
            PricingState.NO_CHARGE,
            43L,
            null,
            null,
            null,
            null,
            null
        );

        record(
            workspace.getId(),
            sample(
                LlmUsageJobType.MENTOR_TURN,
                LlmUsageSourceType.MENTOR_TURN,
                UUID.randomUUID(),
                0,
                "local-model",
                1000,
                200,
                noCharge
            )
        );

        var event = usageRepository.findByWorkspaceId(workspace.getId()).getFirst();
        assertThat(event.getPricingState()).isEqualTo(PricingState.NO_CHARGE);
        assertThat(event.getCostUsd()).isEqualByComparingTo("0");
        assertThat(budgetService.monthToDateCost(workspace.getId())).isEqualByComparingTo("0");
        assertThat(budgetService.isBudgetExhausted(workspace.getId())).isFalse();
        assertThat(meterRegistry.counter("llm.budget.exhausted").count()).isEqualTo(before);
    }

    @Test
    void workspaceFundedSpendNeverCountsTowardTheInstanceBudget() {
        Workspace workspace = setupWorkspace("ledger-byo");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("1.00"));
        workspaceRepository.save(workspace);
        LlmPriceSnapshot workspacePrice = new LlmPriceSnapshot(
            FundingSource.WORKSPACE,
            PricingState.PRICED,
            null,
            84L,
            new BigDecimal("100.00"),
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );

        record(
            workspace.getId(),
            sample(
                LlmUsageJobType.MENTOR_TURN,
                LlmUsageSourceType.MENTOR_TURN,
                UUID.randomUUID(),
                0,
                "byo-model",
                1_000_000,
                1_000_000,
                workspacePrice
            )
        );

        var event = usageRepository.findByWorkspaceId(workspace.getId()).getFirst();
        assertThat(event.getCostUsd()).isEqualByComparingTo("200.00");
        assertThat(event.getFundingSource()).isEqualTo(FundingSource.WORKSPACE);
        assertThat(event.getAppliedWorkspaceModelId()).isEqualTo(84L);
        assertThat(budgetService.monthToDateCost(workspace.getId())).isEqualByComparingTo("0");
        assertThat(budgetService.isBudgetExhausted(workspace.getId())).isFalse();
    }

    @Test
    void sourceAttemptIsTheIdempotencyBoundary() {
        Workspace workspace = setupWorkspace("ledger-dup");
        UUID sourceId = UUID.randomUUID();
        LlmPriceSnapshot price = pricedInstance("1.00", "0.00");

        record(workspace.getId(), agentSample(sourceId, 0, 1000, price));
        record(workspace.getId(), agentSample(sourceId, 0, 1000, price));
        record(workspace.getId(), agentSample(sourceId, 1, 1000, price));

        assertThat(usageRepository.findByWorkspaceId(workspace.getId()))
            .extracting(LlmUsageEvent::getSourceAttempt)
            .containsExactlyInAnyOrder(0, 1);
    }

    @Test
    void crossingTheBudgetFiresTheExhaustedCounterOnce() {
        Workspace workspace = setupWorkspace("ledger-cross");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("1.50"));
        workspaceRepository.save(workspace);
        LlmPriceSnapshot price = pricedInstance("1000.00", "0.00"); // $1.00 per 1000 input tokens
        double before = meterRegistry.counter("llm.budget.exhausted").count();

        record(workspace.getId(), agentSample(UUID.randomUUID(), 0, 1000, price));
        record(workspace.getId(), agentSample(UUID.randomUUID(), 0, 1000, price));
        record(workspace.getId(), agentSample(UUID.randomUUID(), 0, 1000, price));

        assertThat(meterRegistry.counter("llm.budget.exhausted").count()).isEqualTo(before + 1);
        assertThat(budgetService.isBudgetExhausted(workspace.getId())).isTrue();
    }

    @Test
    void submitIsBlockedForAnExhaustedWorkspace() {
        Workspace workspace = setupWorkspace("ledger-block");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("0.50"));
        workspaceRepository.save(workspace);
        bindDetectionTo(workspace, FundingSource.INSTANCE);
        LlmPriceSnapshot price = pricedInstance("500.00", "0.00"); // $0.50 per 1000 input tokens
        record(workspace.getId(), agentSample(UUID.randomUUID(), 0, 1000, price));
        double blockedBefore = blockedCount("instance");

        var job = agentJobService.submit(workspace.getId(), AgentJobType.PULL_REQUEST_REVIEW, null);

        assertThat(job).isEmpty();
        assertThat(blockedCount("instance")).isEqualTo(blockedBefore + 1);
    }

    /**
     * #1368: an exhausted shared-model budget is the host's problem, not the workspace's. Detection
     * bound to the workspace's own provider is a different purse and keeps running — it is refused
     * only later, for lack of a resolvable model in this fixture, never by the host's cap.
     */
    @Test
    void submitIsNotBlockedByTheInstanceCapWhenDetectionRunsOnTheWorkspacesOwnProvider() {
        Workspace workspace = setupWorkspace("ledger-block-byo-open");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("0.50")); // host cap already reached
        workspaceRepository.save(workspace);
        bindDetectionTo(workspace, FundingSource.WORKSPACE);
        record(workspace.getId(), agentSample(UUID.randomUUID(), 0, 1000, pricedInstance("500.00", "0.00")));
        double instanceBlockedBefore = blockedCount("instance");
        double byoBlockedBefore = blockedCount("byo");

        // Past the gate the fixture has no reviewable subject, so submission fails downstream — that
        // is exactly the point: it got past the gate rather than being refused by the host's cap.
        catchThrowable(() -> agentJobService.submit(workspace.getId(), AgentJobType.PULL_REQUEST_REVIEW, null));

        assertThat(blockedCount("instance")).isEqualTo(instanceBlockedBefore);
        assertThat(blockedCount("byo")).isEqualTo(byoBlockedBefore);
    }

    /** The mirror: the workspace's own cap pauses its own-provider detection. */
    @Test
    void submitIsBlockedByTheWorkspacesOwnCapForOwnProviderDetection() {
        Workspace workspace = setupWorkspace("ledger-block-byo");
        workspace.setMonthlyByoLlmBudgetUsd(BigDecimal.ZERO); // an immediate pause switch
        workspaceRepository.save(workspace);
        bindDetectionTo(workspace, FundingSource.WORKSPACE);
        double blockedBefore = blockedCount("byo");

        var job = agentJobService.submit(workspace.getId(), AgentJobType.PULL_REQUEST_REVIEW, null);

        assertThat(job).isEmpty();
        assertThat(blockedCount("byo")).isEqualTo(blockedBefore + 1);
    }

    /** And it must never reach across: a zero BYO cap does not pause shared-model detection. */
    @Test
    void theWorkspacesOwnCapNeverPausesSharedModelDetection() {
        Workspace workspace = setupWorkspace("ledger-byo-cap-instance-work");
        workspace.setMonthlyByoLlmBudgetUsd(BigDecimal.ZERO);
        workspaceRepository.save(workspace);
        bindDetectionTo(workspace, FundingSource.INSTANCE);
        double instanceBlockedBefore = blockedCount("instance");
        double byoBlockedBefore = blockedCount("byo");

        catchThrowable(() -> agentJobService.submit(workspace.getId(), AgentJobType.PULL_REQUEST_REVIEW, null));

        assertThat(blockedCount("instance")).isEqualTo(instanceBlockedBefore);
        assertThat(blockedCount("byo")).isEqualTo(byoBlockedBefore);
    }

    /**
     * The workspace's own cap is measured against PRICED own-provider rows only, and its unverifiable
     * signal comes exclusively from unpriced OWN-PROVIDER rows — an unpriced shared model is the
     * host's blind spot and must never pause the workspace's own work.
     */
    @Test
    void theWorkspacesOwnCapReadsOnlyOwnProviderLedgerRows() {
        Workspace workspace = setupWorkspace("ledger-byo-reads");
        workspace.setMonthlyByoLlmBudgetUsd(new BigDecimal("100.00"));
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("100.00"));
        workspaceRepository.save(workspace);
        // An unpriced INSTANCE-funded event: it makes the host's month unverifiable, nothing more.
        recordUnverifiable(workspace.getId(), agentSample(UUID.randomUUID(), 0, 1000, pricedInstance("3.00", "9.00")));

        LlmBudgetDecision decision = budgetService.decide(workspace);

        assertThat(decision.instanceFunded()).isEqualTo(LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED);
        assertThat(decision.workspaceFunded()).isEqualTo(LlmBudgetBlockReason.NONE);
    }

    @Test
    void anUnpricedOwnProviderEventMakesOnlyTheWorkspacesOwnCapUnverifiable() {
        Workspace workspace = setupWorkspace("ledger-byo-unpriced");
        workspace.setMonthlyByoLlmBudgetUsd(new BigDecimal("100.00"));
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("100.00"));
        workspaceRepository.save(workspace);
        recordUnverifiable(workspace.getId(), agentSample(UUID.randomUUID(), 0, 1000, workspacePriced("3.00", "9.00")));

        LlmBudgetDecision decision = budgetService.decide(workspace);

        assertThat(decision.instanceFunded()).isEqualTo(LlmBudgetBlockReason.NONE);
        assertThat(decision.workspaceFunded()).isEqualTo(LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED);
    }

    @Test
    void unverifiableUsageRetainsAdmissionProvenanceWithoutInventingACost() {
        Workspace workspace = setupWorkspace("ledger-uncosted");
        double uncostedBefore = meterRegistry.counter("llm.usage.uncosted").count();

        recordUnverifiable(workspace.getId(), agentSample(UUID.randomUUID(), 3, 0, pricedInstance("3.00", "9.00")));

        var event = usageRepository.findByWorkspaceId(workspace.getId()).getFirst();
        assertThat(event.getCostUsd()).isNull();
        assertThat(event.getPricingState()).isEqualTo(PricingState.UNPRICED);
        assertThat(event.getSourceAttempt()).isEqualTo(3);
        assertThat(event.getAppliedPriceId()).isEqualTo(42L);
        assertThat(meterRegistry.counter("llm.usage.uncosted").count()).isEqualTo(uncostedBefore + 1);
    }

    @Test
    void unverifiableInstanceUsageMakesTheBudgetVerdictUnverifiable() {
        Workspace workspace = setupWorkspace("ledger-unverifiable");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("100.00"));
        workspaceRepository.save(workspace);

        recordUnverifiable(
            workspace.getId(),
            sample(
                LlmUsageJobType.MENTOR_TURN,
                LlmUsageSourceType.MENTOR_TURN,
                UUID.randomUUID(),
                0,
                "gpt-5",
                0,
                0,
                pricedInstance("3.00", "9.00")
            )
        );

        boolean hasUnpriced = usageRepository.existsUnpricedInstanceFunded(
            workspace.getId(),
            Instant.now().minusSeconds(3600),
            Instant.now().plusSeconds(3600)
        );
        assertThat(hasUnpriced).isTrue();
        assertThat(
            LlmBudgetService.verdictFor(
                budgetService.monthToDateCost(workspace.getId()),
                hasUnpriced,
                workspace.getMonthlyLlmBudgetUsd()
            )
        ).isEqualTo(LlmBudgetVerdict.UNVERIFIABLE);
        assertThat(budgetService.isBudgetExhausted(workspace.getId())).isFalse();
    }

    @Test
    void zeroBudgetPausesImmediatelyEvenWithNoSpend() {
        Workspace workspace = setupWorkspace("ledger-zero");
        workspace.setMonthlyLlmBudgetUsd(BigDecimal.ZERO);
        workspaceRepository.save(workspace);
        bindDetectionTo(workspace, FundingSource.INSTANCE);

        assertThat(agentJobService.submit(workspace.getId(), AgentJobType.ISSUE_REVIEW, null)).isEmpty();
    }

    /**
     * #1368: raising or clearing either cap releases exactly the jobs the claim loop parked on that
     * cap, so the flagship self-serve action ("I raised my cap") takes effect on the next poll rather
     * than up to an hour later. Its precision is the point — it must not fast-forward a crash-retry
     * backoff, which is a different kind of future {@code available_at} entirely.
     */
    @Nested
    @DisplayName("Raising a cap releases the jobs it held (#1368)")
    class BudgetHoldRelease {

        @Test
        @DisplayName("raising the workspace's own cap releases its budget-held jobs immediately")
        void updatingTheOwnProviderCapReleasesBudgetHolds() {
            Workspace workspace = setupWorkspace("hold-byo");
            Instant heldUntil = Instant.now().plus(Duration.ofHours(1));
            AgentJob held = queuedJob(workspace, heldUntil, AgentJob.HOLD_REASON_BUDGET);

            llmUsageService.updateByoBudget(workspace.getId(), new BigDecimal("50.00"));

            AgentJob reloaded = jobRepository.findById(held.getId()).orElseThrow();
            assertThat(reloaded.getHoldReason()).isNull();
            assertThat(reloaded.getAvailableAt()).isBefore(heldUntil);
            assertThat(reloaded.getStatus()).isEqualTo(AgentJobStatus.QUEUED);
        }

        @Test
        @DisplayName("raising the instance cap releases that workspace's budget-held jobs too")
        void updatingTheInstanceCapReleasesBudgetHolds() {
            Workspace workspace = setupWorkspace("hold-instance");
            Instant heldUntil = Instant.now().plus(Duration.ofHours(1));
            AgentJob held = queuedJob(workspace, heldUntil, AgentJob.HOLD_REASON_BUDGET);

            llmUsageAdminService.updateBudget(workspace.getId(), new BigDecimal("50.00"));

            AgentJob reloaded = jobRepository.findById(held.getId()).orElseThrow();
            assertThat(reloaded.getHoldReason()).isNull();
            assertThat(reloaded.getAvailableAt()).isBefore(heldUntil);
        }

        @Test
        @DisplayName("a crash-retry backoff is never fast-forwarded by a cap change")
        void aRetryBackoffIsLeftAlone() {
            // A QUEUED job with a future available_at and NO hold reason is backing off from a failed
            // attempt. Releasing it would send a crash-looping job straight back at a failing upstream.
            Workspace workspace = setupWorkspace("hold-retry-backoff");
            Instant backoffUntil = Instant.now().plus(Duration.ofHours(1));
            AgentJob backingOff = queuedJob(workspace, backoffUntil, null);

            llmUsageService.updateByoBudget(workspace.getId(), new BigDecimal("50.00"));

            AgentJob reloaded = jobRepository.findById(backingOff.getId()).orElseThrow();
            assertThat(reloaded.getAvailableAt()).isCloseTo(backoffUntil, within(1, ChronoUnit.MILLIS));
            assertThat(reloaded.getHoldReason()).isNull();
        }

        @Test
        @DisplayName("only QUEUED jobs are released — a running job is never rewound")
        void aRunningJobIsNeverReleased() {
            Workspace workspace = setupWorkspace("hold-running");
            Instant availableAt = Instant.now().plus(Duration.ofHours(1));
            AgentJob running = queuedJob(workspace, availableAt, AgentJob.HOLD_REASON_BUDGET);
            running.setStatus(AgentJobStatus.RUNNING);
            jobRepository.saveAndFlush(running);

            llmUsageService.updateByoBudget(workspace.getId(), new BigDecimal("50.00"));

            AgentJob reloaded = jobRepository.findById(running.getId()).orElseThrow();
            assertThat(reloaded.getAvailableAt()).isCloseTo(availableAt, within(1, ChronoUnit.MILLIS));
            assertThat(reloaded.getHoldReason()).isEqualTo(AgentJob.HOLD_REASON_BUDGET);
        }

        @Test
        @DisplayName("the release is scoped to one workspace — another tenant's holds are untouched")
        void anotherWorkspacesHoldsAreUntouched() {
            Workspace mine = setupWorkspace("hold-mine");
            Workspace theirs = setupWorkspace("hold-theirs");
            Instant heldUntil = Instant.now().plus(Duration.ofHours(1));
            AgentJob otherTenantJob = queuedJob(theirs, heldUntil, AgentJob.HOLD_REASON_BUDGET);

            llmUsageService.updateByoBudget(mine.getId(), new BigDecimal("50.00"));

            AgentJob reloaded = jobRepository.findById(otherTenantJob.getId()).orElseThrow();
            assertThat(reloaded.getAvailableAt()).isCloseTo(heldUntil, within(1, ChronoUnit.MILLIS));
            assertThat(reloaded.getHoldReason()).isEqualTo(AgentJob.HOLD_REASON_BUDGET);
        }

        @Test
        @DisplayName("clearing a cap releases holds as well — uncapped can pause nothing")
        void clearingTheOwnProviderCapAlsoReleasesHolds() {
            Workspace workspace = setupWorkspace("hold-clear");
            workspace.setMonthlyByoLlmBudgetUsd(BigDecimal.ZERO);
            workspaceRepository.save(workspace);
            AgentJob held = queuedJob(workspace, Instant.now().plus(Duration.ofHours(1)), AgentJob.HOLD_REASON_BUDGET);

            llmUsageService.updateByoBudget(workspace.getId(), null);

            assertThat(jobRepository.findById(held.getId()).orElseThrow().getHoldReason()).isNull();
            assertThat(
                workspaceRepository.findById(workspace.getId()).orElseThrow().getMonthlyByoLlmBudgetUsd()
            ).isNull();
        }
    }
}
