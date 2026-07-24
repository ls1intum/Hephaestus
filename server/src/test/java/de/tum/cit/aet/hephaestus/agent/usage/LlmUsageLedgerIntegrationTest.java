package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobService;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

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
        LlmPriceSnapshot price = pricedInstance("500.00", "0.00"); // $0.50 per 1000 input tokens
        record(workspace.getId(), agentSample(UUID.randomUUID(), 0, 1000, price));
        double blockedBefore = meterRegistry.counter("llm.budget.blocked", "surface", "agent_job").count();

        var job = agentJobService.submit(workspace.getId(), AgentJobType.PULL_REQUEST_REVIEW, null);

        assertThat(job).isEmpty();
        assertThat(meterRegistry.counter("llm.budget.blocked", "surface", "agent_job").count()).isEqualTo(
            blockedBefore + 1
        );
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

        assertThat(agentJobService.submit(workspace.getId(), AgentJobType.ISSUE_REVIEW, null)).isEmpty();
    }
}
