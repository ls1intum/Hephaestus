package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPrice;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPriceRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.PricingMode;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.pricing.ModelPricingService;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Server-side cost derivation, pricing_state assignment, provenance freezing, and the
 * NUMERIC(12,6) storage guard (#1368 slice 6) — the single choke point where a ledger event's
 * cost is resolved. Budget verdict logic itself is a pure function tested in
 * {@link LlmBudgetServiceTest}; the SQL-level "BYO never counts toward the budget" behaviour is
 * covered by {@code LlmUsageLedgerIntegrationTest} against a real database.
 */
class LlmUsageRecorderTest extends BaseUnitTest {

    private static final Long WORKSPACE_ID = 1L;
    private static final Long CONNECTION_ID = 42L;

    @Mock
    private LlmUsageEventRepository usageRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private LlmBudgetService budgetService;

    @Mock
    private ModelPricingService pricingService;

    @Mock
    private LlmModelRepository llmModelRepository;

    @Mock
    private LlmModelPriceRepository llmModelPriceRepository;

    @Mock
    private WorkspaceLlmModelRepository workspaceLlmModelRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    private LlmUsageRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new LlmUsageRecorder(
            usageRepository,
            workspaceRepository,
            budgetService,
            pricingService,
            llmModelRepository,
            llmModelPriceRepository,
            workspaceLlmModelRepository,
            new SimpleMeterRegistry(),
            transactionManager
        );
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        lenient().when(workspaceRepository.getReferenceById(WORKSPACE_ID)).thenReturn(workspace);
        lenient()
            .when(usageRepository.saveAndFlush(any(LlmUsageEvent.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        // The post-write budget-crossing alert reads month-to-date via LlmBudgetService; return zero
        // (below any test's budget) so it never fires unless a test explicitly wants it to.
        lenient().when(budgetService.monthToDateCost(anyLong())).thenReturn(BigDecimal.ZERO);
    }

    private LlmUsageRecorder.LlmUsageSample sample(
        String model,
        long inputTokens,
        long outputTokens,
        long reasoningTokens,
        @Nullable FundingSource connectionScope,
        @Nullable Long connectionId
    ) {
        return new LlmUsageRecorder.LlmUsageSample(
            LlmUsageJobType.PULL_REQUEST_REVIEW,
            UUID.randomUUID(),
            model,
            inputTokens,
            outputTokens,
            0,
            0,
            reasoningTokens,
            1,
            connectionScope,
            connectionId,
            Instant.now()
        );
    }

    private LlmUsageEvent recordAndCapture(LlmUsageRecorder.LlmUsageSample sample) {
        recorder.record(WORKSPACE_ID, sample);
        ArgumentCaptor<LlmUsageEvent> captor = ArgumentCaptor.forClass(LlmUsageEvent.class);
        verify(usageRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private LlmModel instanceModelWithPrice(LlmModelPrice price) {
        LlmModel model = new LlmModel();
        model.setId(7L);
        model.setEnabled(true);
        when(llmModelRepository.findByConnectionIdAndUpstreamModelId(eq(CONNECTION_ID), eq("gpt-5"))).thenReturn(
            List.of(model)
        );
        when(llmModelPriceRepository.findByModelIdAndEffectiveToIsNull(7L)).thenReturn(Optional.of(price));
        return model;
    }

    @Nested
    class InstanceCatalogPricing {

        @Test
        void derivesCostFromTheInstanceCatalogPriceRow() {
            LlmModelPrice price = new LlmModelPrice();
            price.setId(99L);
            price.setPricingMode(PricingMode.PRICED);
            price.setPer1mInputUsd(new BigDecimal("3.00"));
            price.setPer1mOutputUsd(new BigDecimal("9.00"));
            instanceModelWithPrice(price);

            LlmUsageEvent event = recordAndCapture(
                sample("gpt-5", 1_000_000, 1_000_000, 0, FundingSource.INSTANCE, CONNECTION_ID)
            );

            assertThat(event.getPricingState()).isEqualTo(PricingState.PRICED);
            assertThat(event.getFundingSource()).isEqualTo(FundingSource.INSTANCE);
            assertThat(event.getCostUsd()).isEqualByComparingTo("12.00");
            assertThat(event.getAppliedPriceId()).as("provenance frozen onto the event").isEqualTo(99L);
        }

        @Test
        void reasoningTokensAreCostedViaThePer1mReasoningRate() {
            LlmModelPrice price = new LlmModelPrice();
            price.setId(11L);
            price.setPricingMode(PricingMode.PRICED);
            price.setPer1mInputUsd(BigDecimal.ZERO);
            price.setPer1mOutputUsd(BigDecimal.ZERO);
            price.setPer1mReasoningUsd(new BigDecimal("5.00"));
            instanceModelWithPrice(price);

            LlmUsageEvent event = recordAndCapture(
                sample("gpt-5", 0, 0, 1_000_000, FundingSource.INSTANCE, CONNECTION_ID)
            );

            assertThat(event.getReasoningTokens()).isEqualTo(1_000_000);
            assertThat(event.getCostUsd()).isEqualByComparingTo("5.00");
        }

        @Test
        void freeInstanceModelPricesAtExactlyZero() {
            LlmModelPrice price = new LlmModelPrice();
            price.setId(21L);
            price.setPricingMode(PricingMode.FREE);
            instanceModelWithPrice(price);

            LlmUsageEvent event = recordAndCapture(
                sample("gpt-5", 1000, 200, 0, FundingSource.INSTANCE, CONNECTION_ID)
            );

            assertThat(event.getPricingState()).isEqualTo(PricingState.FREE);
            assertThat(event.getCostUsd()).isEqualByComparingTo("0");
            assertThat(event.getAppliedPriceId()).isEqualTo(21L);
        }

        @Test
        void unpricedInstanceModelStoresNullCostNotZero() {
            LlmModelPrice price = new LlmModelPrice();
            price.setId(31L);
            price.setPricingMode(PricingMode.UNPRICED);
            instanceModelWithPrice(price);

            LlmUsageEvent event = recordAndCapture(
                sample("gpt-5", 1000, 200, 0, FundingSource.INSTANCE, CONNECTION_ID)
            );

            assertThat(event.getPricingState()).isEqualTo(PricingState.UNPRICED);
            assertThat(event.getCostUsd()).isNull();
        }

        @Test
        void noMatchingCatalogModelIsUnpricedWithNoProvenance() {
            when(llmModelRepository.findByConnectionIdAndUpstreamModelId(eq(CONNECTION_ID), eq("ghost"))).thenReturn(
                List.of()
            );

            LlmUsageEvent event = recordAndCapture(
                sample("ghost", 1000, 200, 0, FundingSource.INSTANCE, CONNECTION_ID)
            );

            assertThat(event.getPricingState()).isEqualTo(PricingState.UNPRICED);
            assertThat(event.getCostUsd()).isNull();
            assertThat(event.getAppliedPriceId()).isNull();
        }
    }

    @Nested
    class WorkspaceByoPricing {

        @Test
        void derivesCostFromTheWorkspacesOwnInlinePrice() {
            WorkspaceLlmModel model = new WorkspaceLlmModel();
            model.setId(55L);
            model.setEnabled(true);
            model.setPricingMode(PricingMode.PRICED);
            model.setPer1mInputUsd(new BigDecimal("10.00"));
            model.setPer1mOutputUsd(new BigDecimal("20.00"));
            when(
                workspaceLlmModelRepository.findByConnectionIdAndUpstreamModelId(eq(CONNECTION_ID), eq("byo-model"))
            ).thenReturn(List.of(model));

            LlmUsageEvent event = recordAndCapture(
                sample("byo-model", 1_000_000, 1_000_000, 0, FundingSource.WORKSPACE, CONNECTION_ID)
            );

            assertThat(event.getPricingState()).isEqualTo(PricingState.PRICED);
            assertThat(event.getFundingSource()).isEqualTo(FundingSource.WORKSPACE);
            assertThat(event.getCostUsd()).isEqualByComparingTo("30.00");
            // applied_price_id's documented meaning is strictly an instance llm_model_price row —
            // a BYO model's id is never written there.
            assertThat(event.getAppliedPriceId()).isNull();
        }

        @Test
        void freeByoModelPricesAtExactlyZero() {
            WorkspaceLlmModel model = new WorkspaceLlmModel();
            model.setId(56L);
            model.setEnabled(true);
            model.setPricingMode(PricingMode.FREE);
            when(
                workspaceLlmModelRepository.findByConnectionIdAndUpstreamModelId(eq(CONNECTION_ID), eq("byo-free"))
            ).thenReturn(List.of(model));

            LlmUsageEvent event = recordAndCapture(
                sample("byo-free", 1000, 200, 0, FundingSource.WORKSPACE, CONNECTION_ID)
            );

            assertThat(event.getPricingState()).isEqualTo(PricingState.FREE);
            assertThat(event.getCostUsd()).isEqualByComparingTo("0");
            assertThat(event.getFundingSource()).isEqualTo(FundingSource.WORKSPACE);
        }
    }

    @Nested
    class LegacyFallback {

        @Test
        void noCatalogBindingFallsBackToTheLegacyPricingTable() {
            when(pricingService.computeCost(eq("legacy-model"), eq(1000L), eq(1000L), eq(0L), eq(0L))).thenReturn(
                Optional.of(new BigDecimal("0.000750"))
            );

            LlmUsageEvent event = recordAndCapture(sample("legacy-model", 1000, 1000, 0, null, null));

            assertThat(event.getPricingState()).isEqualTo(PricingState.PRICED);
            assertThat(event.getFundingSource()).isEqualTo(FundingSource.INSTANCE);
            assertThat(event.getCostUsd()).isEqualByComparingTo("0.000750");
            assertThat(event.getAppliedPriceId()).isNull();
        }

        @Test
        void noCatalogBindingAndNoLegacyPriceIsUnpriced() {
            when(pricingService.computeCost(eq("unknown-model"), eq(1000L), eq(200L), eq(0L), eq(0L))).thenReturn(
                Optional.empty()
            );

            LlmUsageEvent event = recordAndCapture(sample("unknown-model", 1000, 200, 0, null, null));

            assertThat(event.getPricingState()).isEqualTo(PricingState.UNPRICED);
            assertThat(event.getCostUsd()).isNull();
        }
    }

    @Nested
    class NumericGuard {

        @Test
        void aNonZeroCostThatRoundsToZeroStoresTheMinimumRepresentableAmountInstead() {
            LlmModelPrice price = new LlmModelPrice();
            price.setId(41L);
            price.setPricingMode(PricingMode.PRICED);
            // 1 token * (0.0000001 / 1e6) rounds to 0.000000 at 6dp, but is genuinely non-zero.
            price.setPer1mInputUsd(new BigDecimal("0.0000001"));
            price.setPer1mOutputUsd(BigDecimal.ZERO);
            instanceModelWithPrice(price);

            LlmUsageEvent event = recordAndCapture(sample("gpt-5", 1, 0, 0, FundingSource.INSTANCE, CONNECTION_ID));

            assertThat(event.getCostUsd()).isEqualByComparingTo("0.000001");
        }

        @Test
        void aCostAtOrAboveTenToTheTwelveIsClampedToTheMaxRepresentableAmount() {
            // NUMERIC(18,6) (widened from NUMERIC(12,6) — #1368 migration-correctness fix, changelog
            // 1784566728230-17) overflows at 10^12; a rate this absurd is unreachable in practice and
            // exists only to exercise the clamp.
            LlmModelPrice price = new LlmModelPrice();
            price.setId(42L);
            price.setPricingMode(PricingMode.PRICED);
            price.setPer1mInputUsd(new BigDecimal("2000000000000000")); // absurdly high rate
            price.setPer1mOutputUsd(BigDecimal.ZERO);
            instanceModelWithPrice(price);

            LlmUsageEvent event = recordAndCapture(
                sample("gpt-5", 1_000_000, 0, 0, FundingSource.INSTANCE, CONNECTION_ID)
            );

            assertThat(event.getCostUsd()).isEqualByComparingTo("999999999999.999999");
        }

        @Test
        void aCostWellBelowTenToTheTwelveIsNeverClamped() {
            // A cost that would have overflowed the OLD NUMERIC(12,6) column (> $999,999.999999) must
            // now be stored exactly, unclamped, under the widened NUMERIC(18,6) column.
            LlmModelPrice price = new LlmModelPrice();
            price.setId(44L);
            price.setPricingMode(PricingMode.PRICED);
            price.setPer1mInputUsd(new BigDecimal("5000000")); // $5M per 1M tokens
            price.setPer1mOutputUsd(BigDecimal.ZERO);
            instanceModelWithPrice(price);

            LlmUsageEvent event = recordAndCapture(
                sample("gpt-5", 1_000_000, 0, 0, FundingSource.INSTANCE, CONNECTION_ID)
            );

            assertThat(event.getCostUsd()).isEqualByComparingTo("5000000.000000");
        }

        @Test
        void anExactMidpointRoundsHalfEvenNotHalfUp() {
            LlmModelPrice price = new LlmModelPrice();
            price.setId(43L);
            price.setPricingMode(PricingMode.PRICED);
            // 1 token @ $2.50 / 1M = exactly 0.0000025 — a genuine tie between 0.000002 and
            // 0.000003. HALF_EVEN rounds to the even neighbour (0.000002); HALF_UP would give
            // 0.000003. Pins the rounding mode, not just "some rounding happens".
            price.setPer1mInputUsd(new BigDecimal("2.50"));
            price.setPer1mOutputUsd(BigDecimal.ZERO);
            instanceModelWithPrice(price);

            LlmUsageEvent event = recordAndCapture(sample("gpt-5", 1, 0, 0, FundingSource.INSTANCE, CONNECTION_ID));

            assertThat(event.getCostUsd()).isEqualByComparingTo("0.000002");
        }
    }
}
