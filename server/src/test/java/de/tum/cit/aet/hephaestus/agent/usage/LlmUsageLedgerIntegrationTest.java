package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPrice;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPriceRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.PricingMode;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
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
 * Ledger write path + server-side cost derivation + budget enforcement, exercised against the
 * real database (#1368 slice 6): instance-catalog pricing, workspace BYO pricing, the legacy
 * pricing-table fallback, the recorder's failure isolation (duplicate source), the
 * budget-crossing alert counter, BYO spend exclusion from the budget, and the
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

    @Autowired
    private LlmConnectionRepository llmConnectionRepository;

    @Autowired
    private LlmModelRepository llmModelRepository;

    @Autowired
    private LlmModelPriceRepository llmModelPriceRepository;

    @Autowired
    private WorkspaceLlmConnectionRepository workspaceLlmConnectionRepository;

    @Autowired
    private WorkspaceLlmModelRepository workspaceLlmModelRepository;

    private Workspace setupWorkspace(String slug) {
        User owner = persistUser(slug + "-owner");
        return createWorkspace(slug, "Usage " + slug, slug + "-org", AccountType.ORG, owner);
    }

    private LlmUsageRecorder.LlmUsageSample sample(UUID sourceId) {
        return new LlmUsageRecorder.LlmUsageSample(
            LlmUsageJobType.PULL_REQUEST_REVIEW,
            sourceId,
            "no-catalog-binding-model",
            1000,
            200,
            50,
            10,
            0,
            3,
            null,
            null,
            Instant.now()
        );
    }

    private LlmConnection instanceConnection(String slug) {
        LlmConnection connection = new LlmConnection();
        connection.setSlug(slug);
        connection.setDisplayName(slug);
        connection.setBaseUrl("https://example.test");
        connection.setApiProtocol("openai-completions");
        connection.setEnabled(true);
        return llmConnectionRepository.save(connection);
    }

    private LlmModel instanceModel(LlmConnection connection, String slug, String upstreamModelId) {
        LlmModel model = new LlmModel();
        model.setConnection(connection);
        model.setSlug(slug);
        model.setDisplayName(slug);
        model.setUpstreamModelId(upstreamModelId);
        model.setEnabled(true);
        return llmModelRepository.save(model);
    }

    private LlmModelPrice openInstancePrice(
        LlmModel model,
        PricingMode mode,
        String perMInput,
        String perMOutput,
        String perMReasoning
    ) {
        LlmModelPrice price = new LlmModelPrice();
        price.setModel(model);
        price.setPricingMode(mode);
        if (mode == PricingMode.PRICED) {
            price.setPer1mInputUsd(new BigDecimal(perMInput));
            price.setPer1mOutputUsd(new BigDecimal(perMOutput));
            if (perMReasoning != null) {
                price.setPer1mReasoningUsd(new BigDecimal(perMReasoning));
            }
        }
        price.setEffectiveFrom(Instant.now().minusSeconds(60));
        return llmModelPriceRepository.save(price);
    }

    private WorkspaceLlmConnection workspaceConnection(Workspace workspace, String slug) {
        WorkspaceLlmConnection connection = new WorkspaceLlmConnection();
        connection.setWorkspace(workspace);
        connection.setSlug(slug);
        connection.setDisplayName(slug);
        connection.setBaseUrl("https://example.test");
        connection.setApiProtocol("openai-completions");
        connection.setEnabled(true);
        return workspaceLlmConnectionRepository.save(connection);
    }

    private WorkspaceLlmModel workspaceModel(
        Workspace workspace,
        WorkspaceLlmConnection connection,
        String slug,
        String upstreamModelId,
        PricingMode mode,
        String perMInput,
        String perMOutput
    ) {
        WorkspaceLlmModel model = new WorkspaceLlmModel();
        model.setWorkspace(workspace);
        model.setConnection(connection);
        model.setSlug(slug);
        model.setDisplayName(slug);
        model.setUpstreamModelId(upstreamModelId);
        model.setEnabled(true);
        model.setPricingMode(mode);
        if (mode == PricingMode.PRICED) {
            model.setPer1mInputUsd(new BigDecimal(perMInput));
            model.setPer1mOutputUsd(new BigDecimal(perMOutput));
        }
        return workspaceLlmModelRepository.save(model);
    }

    @Test
    void recordAppendsOneLedgerRowPricedFromTheInstanceCatalog() {
        Workspace workspace = setupWorkspace("ledger-instance-priced");
        LlmConnection connection = instanceConnection("ledger-instance-priced-conn");
        LlmModel model = instanceModel(connection, "gpt-5", "gpt-5-upstream");
        LlmModelPrice price = openInstancePrice(model, PricingMode.PRICED, "3.00", "9.00", "1.50");
        UUID sourceId = UUID.randomUUID();

        recorder.record(
            workspace.getId(),
            new LlmUsageRecorder.LlmUsageSample(
                LlmUsageJobType.PULL_REQUEST_REVIEW,
                sourceId,
                "gpt-5-upstream",
                1_000_000,
                1_000_000,
                0,
                0,
                1_000_000,
                3,
                FundingSource.INSTANCE,
                connection.getId(),
                Instant.now()
            )
        );

        var events = usageRepository.findByWorkspaceId(workspace.getId());
        assertThat(events).hasSize(1);
        var event = events.getFirst();
        assertThat(event.getSourceId()).isEqualTo(sourceId);
        // 1M input @ $3/1M + 1M output @ $9/1M + 1M reasoning @ $1.50/1M = 13.50
        assertThat(event.getCostUsd()).isEqualByComparingTo("13.50");
        assertThat(event.getPricingState()).isEqualTo(PricingState.PRICED);
        assertThat(event.getFundingSource()).isEqualTo(FundingSource.INSTANCE);
        assertThat(event.getAppliedPriceId()).isEqualTo(price.getId());
        assertThat(budgetService.monthToDateCost(workspace.getId())).isEqualByComparingTo("13.50");
    }

    @Test
    void freeInstanceCatalogModelIsRecordedAsZeroCostAndNeverAlerts() {
        Workspace workspace = setupWorkspace("ledger-instance-free");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("50.00"));
        workspaceRepository.save(workspace);
        LlmConnection connection = instanceConnection("ledger-instance-free-conn");
        LlmModel model = instanceModel(connection, "local-llama", "local-llama-upstream");
        openInstancePrice(model, PricingMode.FREE, null, null, null);
        double before = meterRegistry.counter("llm.budget.exhausted").count();

        recorder.record(
            workspace.getId(),
            new LlmUsageRecorder.LlmUsageSample(
                LlmUsageJobType.MENTOR_TURN,
                UUID.randomUUID(),
                "local-llama-upstream",
                1000,
                200,
                0,
                0,
                0,
                1,
                FundingSource.INSTANCE,
                connection.getId(),
                Instant.now()
            )
        );

        var event = usageRepository.findByWorkspaceId(workspace.getId()).getFirst();
        assertThat(event.getPricingState()).isEqualTo(PricingState.FREE);
        assertThat(event.getCostUsd()).isEqualByComparingTo("0");
        assertThat(budgetService.monthToDateCost(workspace.getId())).isEqualByComparingTo("0");
        assertThat(budgetService.isBudgetExhausted(workspace.getId())).isFalse();
        // A $0 event can never be the one that "crosses" a cap.
        assertThat(meterRegistry.counter("llm.budget.exhausted").count()).isEqualTo(before);
    }

    @Test
    void byoWorkspaceCatalogSpendNeverCountsTowardTheInstanceBudget() {
        Workspace workspace = setupWorkspace("ledger-byo");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("1.00"));
        workspaceRepository.save(workspace);
        WorkspaceLlmConnection connection = workspaceConnection(workspace, "byo-conn");
        workspaceModel(workspace, connection, "byo-model", "byo-upstream", PricingMode.PRICED, "100.00", "100.00");

        recorder.record(
            workspace.getId(),
            new LlmUsageRecorder.LlmUsageSample(
                LlmUsageJobType.MENTOR_TURN,
                UUID.randomUUID(),
                "byo-upstream",
                1_000_000,
                1_000_000,
                0,
                0,
                0,
                1,
                FundingSource.WORKSPACE,
                connection.getId(),
                Instant.now()
            )
        );

        var event = usageRepository.findByWorkspaceId(workspace.getId()).getFirst();
        // 1M input @ $100/1M + 1M output @ $100/1M = 200.00 — real BYO spend, well above the $1 cap.
        assertThat(event.getCostUsd()).isEqualByComparingTo("200.00");
        assertThat(event.getFundingSource()).isEqualTo(FundingSource.WORKSPACE);
        assertThat(event.getAppliedPriceId()).isNull();
        // The budgeted sum only counts INSTANCE-funded spend — BYO never contributes.
        assertThat(budgetService.monthToDateCost(workspace.getId())).isEqualByComparingTo("0");
        assertThat(budgetService.isBudgetExhausted(workspace.getId())).isFalse();
    }

    @Test
    void duplicateSourceIsSwallowedAndBillsOnce() {
        Workspace workspace = setupWorkspace("ledger-dup");
        UUID sourceId = UUID.randomUUID();

        recorder.record(workspace.getId(), sample(sourceId));
        recorder.record(workspace.getId(), sample(sourceId));

        assertThat(usageRepository.findByWorkspaceId(workspace.getId())).hasSize(1);
    }

    @Test
    void crossingTheBudgetFiresTheExhaustedCounterOnce() {
        Workspace workspace = setupWorkspace("ledger-cross");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("1.50"));
        workspaceRepository.save(workspace);
        LlmConnection connection = instanceConnection("ledger-cross-conn");
        LlmModel model = instanceModel(connection, "priced", "priced-upstream");
        openInstancePrice(model, PricingMode.PRICED, "1000000.00", "0.00", null); // $1.00 per 1000 input tokens
        double before = meterRegistry.counter("llm.budget.exhausted").count();

        recorder.record(workspace.getId(), instanceSample(connection.getId(), UUID.randomUUID(), 1000)); // $1.00, below cap
        recorder.record(workspace.getId(), instanceSample(connection.getId(), UUID.randomUUID(), 1000)); // crosses cap
        recorder.record(workspace.getId(), instanceSample(connection.getId(), UUID.randomUUID(), 1000)); // already over — no re-alert

        assertThat(meterRegistry.counter("llm.budget.exhausted").count()).isEqualTo(before + 1);
        assertThat(budgetService.isBudgetExhausted(workspace.getId())).isTrue();
    }

    private LlmUsageRecorder.LlmUsageSample instanceSample(Long connectionId, UUID sourceId, long inputTokens) {
        return new LlmUsageRecorder.LlmUsageSample(
            LlmUsageJobType.PULL_REQUEST_REVIEW,
            sourceId,
            "priced-upstream",
            inputTokens,
            0,
            0,
            0,
            0,
            1,
            FundingSource.INSTANCE,
            connectionId,
            Instant.now()
        );
    }

    @Test
    void submitIsBlockedForAnExhaustedWorkspace() {
        Workspace workspace = setupWorkspace("ledger-block");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("0.50"));
        workspaceRepository.save(workspace);
        LlmConnection connection = instanceConnection("ledger-block-conn");
        LlmModel model = instanceModel(connection, "priced", "priced-upstream");
        openInstancePrice(model, PricingMode.PRICED, "500000.00", "0.00", null); // $0.50 per 1000 input tokens
        recorder.record(workspace.getId(), instanceSample(connection.getId(), UUID.randomUUID(), 1000));
        double blockedBefore = meterRegistry.counter("llm.budget.blocked", "surface", "agent_job").count();

        var job = agentJobService.submit(workspace.getId(), AgentJobType.PULL_REQUEST_REVIEW, null);

        assertThat(job).isEmpty();
        assertThat(meterRegistry.counter("llm.budget.blocked", "surface", "agent_job").count()).isEqualTo(
            blockedBefore + 1
        );
    }

    @Test
    void unpricedModelWithNoCatalogBindingIsRecordedUncostedAndCounted() {
        Workspace workspace = setupWorkspace("ledger-uncosted");
        double uncostedBefore = meterRegistry.counter("llm.usage.uncosted").count();

        recorder.record(workspace.getId(), sample(UUID.randomUUID()));

        var event = usageRepository.findByWorkspaceId(workspace.getId()).getFirst();
        assertThat(event.getCostUsd()).isNull();
        assertThat(event.getPricingState()).isEqualTo(PricingState.UNPRICED);
        assertThat(event.getInputTokens()).isEqualTo(1000);
        assertThat(meterRegistry.counter("llm.usage.uncosted").count()).isEqualTo(uncostedBefore + 1);
    }

    @Test
    void unboundInstanceCatalogEventMakesTheVerdictUnverifiableInsteadOfBlocking() {
        Workspace workspace = setupWorkspace("ledger-unverifiable");
        workspace.setMonthlyLlmBudgetUsd(new BigDecimal("100.00"));
        workspaceRepository.save(workspace);
        LlmConnection connection = instanceConnection("ledger-unverifiable-conn");
        // No LlmModel registered for this connection+upstream id combination: an instance-funded
        // catalog binding that can't be resolved to a price.

        recorder.record(
            workspace.getId(),
            new LlmUsageRecorder.LlmUsageSample(
                LlmUsageJobType.MENTOR_TURN,
                UUID.randomUUID(),
                "unregistered-upstream-id",
                1000,
                200,
                0,
                0,
                0,
                1,
                FundingSource.INSTANCE,
                connection.getId(),
                Instant.now()
            )
        );

        boolean hasUnpriced = usageRepository.existsUnpricedInstanceFunded(
            workspace.getId(),
            Instant.now().minusSeconds(3600),
            Instant.now().plusSeconds(3600)
        );
        assertThat(hasUnpriced).isTrue();
        LlmBudgetVerdict verdict = LlmBudgetService.verdictFor(
            budgetService.monthToDateCost(workspace.getId()),
            hasUnpriced,
            workspace.getMonthlyLlmBudgetUsd()
        );
        assertThat(verdict).isEqualTo(LlmBudgetVerdict.UNVERIFIABLE);
        // UNVERIFIABLE never blocks submission in v1 — the workspace is not exhausted.
        assertThat(budgetService.isBudgetExhausted(workspace.getId())).isFalse();
    }

    @Test
    void missingCatalogBindingFallsBackToTheLegacyModelPricingTable() {
        Workspace workspace = setupWorkspace("ledger-legacy");
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
                0,
                1,
                null, // no catalog binding — legacy config
                null,
                Instant.now()
            )
        );

        var event = usageRepository.findByWorkspaceId(workspace.getId()).getFirst();
        assertThat(event.getPricingState()).isEqualTo(PricingState.PRICED);
        assertThat(event.getFundingSource()).isEqualTo(FundingSource.INSTANCE);
        assertThat(budgetService.monthToDateCost(workspace.getId())).isEqualByComparingTo("0.000750");
    }

    @Test
    void zeroBudgetPausesImmediatelyEvenWithNoSpend() {
        Workspace workspace = setupWorkspace("ledger-zero");
        workspace.setMonthlyLlmBudgetUsd(BigDecimal.ZERO);
        workspaceRepository.save(workspace);

        assertThat(agentJobService.submit(workspace.getId(), AgentJobType.ISSUE_REVIEW, null)).isEmpty();
    }
}
