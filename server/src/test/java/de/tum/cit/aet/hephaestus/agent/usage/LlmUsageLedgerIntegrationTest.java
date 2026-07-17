package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobService;
import de.tum.cit.aet.hephaestus.agent.pricing.ModelPricing;
import de.tum.cit.aet.hephaestus.agent.pricing.ModelPricingRepository;
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

/**
 * Ledger write path + budget enforcement, exercised against the real database: the recorder's
 * failure isolation (duplicate source), the budget-crossing alert counter, and the
 * {@code AgentJobService.submit} choke point rejecting work for an exhausted workspace.
 */
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
    private ModelPricingRepository pricingRepository;

    private Workspace setupWorkspace(String slug) {
        User owner = persistUser(slug + "-owner");
        return createWorkspace(slug, "Usage " + slug, slug + "-org", AccountType.ORG, owner);
    }

    private LlmUsageRecorder.LlmUsageSample sample(UUID sourceId, String costUsd) {
        return new LlmUsageRecorder.LlmUsageSample(
            LlmUsageJobType.PULL_REQUEST_REVIEW,
            sourceId,
            "claude-sonnet-5",
            1000,
            200,
            50,
            10,
            3,
            costUsd != null ? new BigDecimal(costUsd) : null,
            Instant.now()
        );
    }

    @Test
    void recordAppendsOneLedgerRow() {
        Workspace workspace = setupWorkspace("ledger-write");
        UUID sourceId = UUID.randomUUID();

        recorder.record(workspace.getId(), sample(sourceId, "0.123456"));

        var events = usageRepository.findByWorkspaceId(workspace.getId());
        assertThat(events).hasSize(1);
        var event = events.getFirst();
        assertThat(event.getSourceId()).isEqualTo(sourceId);
        assertThat(event.getJobType()).isEqualTo(LlmUsageJobType.PULL_REQUEST_REVIEW);
        assertThat(event.getInputTokens()).isEqualTo(1000);
        assertThat(event.getOutputTokens()).isEqualTo(200);
        assertThat(event.getTotalCalls()).isEqualTo(3);
        assertThat(event.getCostUsd()).isEqualByComparingTo("0.123456");
        assertThat(budgetService.monthToDateCost(workspace.getId())).isEqualByComparingTo("0.123456");
    }

    @Test
    void duplicateSourceIsSwallowedAndBillsOnce() {
        Workspace workspace = setupWorkspace("ledger-dup");
        UUID sourceId = UUID.randomUUID();

        recorder.record(workspace.getId(), sample(sourceId, "1.00"));
        recorder.record(workspace.getId(), sample(sourceId, "1.00"));

        assertThat(usageRepository.findByWorkspaceId(workspace.getId())).hasSize(1);
        assertThat(budgetService.monthToDateCost(workspace.getId())).isEqualByComparingTo("1.00");
    }

    @Test
    void crossingTheBudgetFiresTheExhaustedCounterOnce() {
        Workspace workspace = setupWorkspace("ledger-cross");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("1.50"));
        workspaceRepository.save(workspace);
        double before = meterRegistry.counter("llm.budget.exhausted").count();

        recorder.record(workspace.getId(), sample(UUID.randomUUID(), "1.00")); // below cap
        recorder.record(workspace.getId(), sample(UUID.randomUUID(), "1.00")); // crosses cap
        recorder.record(workspace.getId(), sample(UUID.randomUUID(), "1.00")); // already over — no re-alert

        assertThat(meterRegistry.counter("llm.budget.exhausted").count()).isEqualTo(before + 1);
        assertThat(budgetService.isBudgetExhausted(workspace.getId())).isTrue();
    }

    @Test
    void submitIsBlockedForAnExhaustedWorkspace() {
        Workspace workspace = setupWorkspace("ledger-block");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("0.50"));
        workspaceRepository.save(workspace);
        recorder.record(workspace.getId(), sample(UUID.randomUUID(), "0.50"));
        double blockedBefore = meterRegistry.counter("llm.budget.blocked", "surface", "agent_job").count();

        var job = agentJobService.submit(workspace.getId(), AgentJobType.PULL_REQUEST_REVIEW, null);

        assertThat(job).isEmpty();
        assertThat(meterRegistry.counter("llm.budget.blocked", "surface", "agent_job").count()).isEqualTo(
            blockedBefore + 1
        );
    }

    @Test
    void unpricedModelWithNoReportedCostIsRecordedUncostedAndCounted() {
        Workspace workspace = setupWorkspace("ledger-uncosted");
        double uncostedBefore = meterRegistry.counter("llm.usage.uncosted").count();

        recorder.record(
            workspace.getId(),
            new LlmUsageRecorder.LlmUsageSample(
                LlmUsageJobType.MENTOR_TURN,
                UUID.randomUUID(),
                "model-nobody-priced",
                1000,
                200,
                0,
                0,
                1,
                null,
                Instant.now()
            )
        );

        var event = usageRepository.findByWorkspaceId(workspace.getId()).getFirst();
        assertThat(event.getCostUsd()).isNull();
        assertThat(event.getInputTokens()).isEqualTo(1000);
        assertThat(meterRegistry.counter("llm.usage.uncosted").count()).isEqualTo(uncostedBefore + 1);
    }

    @Test
    void missingCostIsDerivedFromTheModelPricingTable() {
        Workspace workspace = setupWorkspace("ledger-derived");
        // The suite builds its schema with ddl-auto, so the changelog's model_pricing seed is absent —
        // register the row this test needs explicitly.
        ModelPricing pricing = new ModelPricing();
        pricing.setModelId("priced-model");
        pricing.setPer1kInputUsd(new BigDecimal("0.000150"));
        pricing.setPer1kOutputUsd(new BigDecimal("0.000600"));
        pricing.setPer1kCacheReadUsd(BigDecimal.ZERO);
        pricing.setPer1kCacheWriteUsd(BigDecimal.ZERO);
        pricing.setValidFrom(Instant.now().minusSeconds(60));
        pricingRepository.save(pricing);

        recorder.record(
            workspace.getId(),
            new LlmUsageRecorder.LlmUsageSample(
                LlmUsageJobType.MENTOR_TURN,
                UUID.randomUUID(),
                "priced-model", // 0.000150 per 1k input + 0.000600 per 1k output
                1000,
                1000,
                0,
                0,
                1,
                null,
                Instant.now()
            )
        );

        assertThat(budgetService.monthToDateCost(workspace.getId())).isEqualByComparingTo("0.000750");
    }

    @Test
    void implausibleReportedCostIsRejectedInsteadOfPoisoningTheBudget() {
        Workspace workspace = setupWorkspace("ledger-implausible");

        // A Pi-side regression reporting a negative cost would SUBTRACT from month-to-date and
        // silently un-pause an exhausted workspace.
        recorder.record(workspace.getId(), sample(UUID.randomUUID(), "-50.00"));

        var event = usageRepository.findByWorkspaceId(workspace.getId()).getFirst();
        assertThat(event.getCostUsd()).isNull(); // model has no pricing row → unknown, not negative
        assertThat(budgetService.monthToDateCost(workspace.getId())).isEqualByComparingTo("0");
    }

    @Test
    void zeroBudgetPausesImmediatelyEvenWithNoSpend() {
        Workspace workspace = setupWorkspace("ledger-zero");
        workspace.setMonthlyLlmBudgetUsd(BigDecimal.ZERO);
        workspaceRepository.save(workspace);

        assertThat(agentJobService.submit(workspace.getId(), AgentJobType.ISSUE_REVIEW, null)).isEmpty();
    }
}
