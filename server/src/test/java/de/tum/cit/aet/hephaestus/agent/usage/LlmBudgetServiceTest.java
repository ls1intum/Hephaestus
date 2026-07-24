package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class LlmBudgetServiceTest extends BaseUnitTest {

    @Mock
    private LlmUsageEventRepository usageRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    private LlmBudgetService budgetService;

    @BeforeEach
    void setUp() {
        budgetService = new LlmBudgetService(
            usageRepository,
            workspaceRepository,
            new io.micrometer.core.instrument.simple.SimpleMeterRegistry()
        );
    }

    private Workspace workspaceWithBudget(BigDecimal budget) {
        Workspace workspace = new Workspace();
        workspace.setId(42L);
        workspace.setMonthlyLlmBudgetUsd(budget);
        return workspace;
    }

    private void stubMonthSpend(String spend) {
        when(usageRepository.sumCost(eq(42L), any(Instant.class), any(Instant.class))).thenReturn(
            new BigDecimal(spend)
        );
    }

    private void stubHasUnpriced(boolean hasUnpriced) {
        when(usageRepository.existsUnpricedInstanceFunded(eq(42L), any(Instant.class), any(Instant.class))).thenReturn(
            hasUnpriced
        );
    }

    @Nested
    class BudgetEvaluation {

        @Test
        void uncappedWorkspaceIsNeverExhausted() {
            assertThat(budgetService.isBudgetExhausted(workspaceWithBudget(null))).isFalse();
        }

        @Test
        void spendBelowBudgetIsNotExhausted() {
            stubMonthSpend("9.99");
            assertThat(budgetService.isBudgetExhausted(workspaceWithBudget(new BigDecimal("10.00")))).isFalse();
        }

        @Test
        void spendAtBudgetIsExhausted() {
            stubMonthSpend("10.00");
            assertThat(budgetService.isBudgetExhausted(workspaceWithBudget(new BigDecimal("10.00")))).isTrue();
        }

        @Test
        void zeroBudgetActsAsImmediatePauseSwitch() {
            stubMonthSpend("0");
            assertThat(budgetService.isBudgetExhausted(workspaceWithBudget(BigDecimal.ZERO))).isTrue();
        }

        @Test
        void unknownWorkspaceIdIsNotExhausted() {
            when(workspaceRepository.findById(99L)).thenReturn(java.util.Optional.empty());
            assertThat(budgetService.isBudgetExhausted(99L)).isFalse();
        }
    }

    /**
     * #1368: {@link LlmBudgetService#blockReason} blocks a capped workspace on EXHAUSTED and on an
     * UNVERIFIABLE month (a cap whose true spend can't be confirmed is not a cap). Uncapped
     * workspaces are never blocked.
     */
    @Nested
    class BlockReason {

        @Test
        void uncappedWorkspaceIsNeverBlocked() {
            assertThat(budgetService.blockReason(workspaceWithBudget(null))).isEqualTo(LlmBudgetBlockReason.NONE);
            // Never even asked the ledger — uncapped short-circuits first.
            verify(usageRepository, never()).sumCost(any(), any(), any());
        }

        @Test
        void exhaustedBudgetBlocks() {
            stubMonthSpend("10.00");

            assertThat(budgetService.blockReason(workspaceWithBudget(new BigDecimal("10.00")))).isEqualTo(
                LlmBudgetBlockReason.EXHAUSTED
            );
            // EXHAUSTED is provable from the priced sum alone — never needs the unpriced-event query.
            verify(usageRepository, never()).existsUnpricedInstanceFunded(any(), any(), any());
        }

        @Test
        void withinBudgetAndNoUnpricedUsageIsNeverBlocked() {
            stubMonthSpend("1.00");
            stubHasUnpriced(false);

            assertThat(budgetService.blockReason(workspaceWithBudget(new BigDecimal("10.00")))).isEqualTo(
                LlmBudgetBlockReason.NONE
            );
        }

        @Test
        void unverifiableMonthBlocksACappedWorkspace() {
            stubMonthSpend("1.00");
            stubHasUnpriced(true);

            assertThat(budgetService.blockReason(workspaceWithBudget(new BigDecimal("10.00")))).isEqualTo(
                LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED
            );
        }

        @Test
        void unknownWorkspaceIdIsNeverBlocked() {
            when(workspaceRepository.findById(99L)).thenReturn(java.util.Optional.empty());

            assertThat(budgetService.blockReason(99L)).isEqualTo(LlmBudgetBlockReason.NONE);
        }
    }

    @Nested
    class Verdict {

        @Test
        void withinBudgetWithNoUnpricedEventsIsWithin() {
            assertThat(LlmBudgetService.verdictFor(new BigDecimal("5.00"), false, new BigDecimal("10.00"))).isEqualTo(
                LlmBudgetVerdict.WITHIN
            );
        }

        @Test
        void pricedSumAtOrAboveTheCapIsExhausted() {
            assertThat(LlmBudgetService.verdictFor(new BigDecimal("10.00"), false, new BigDecimal("10.00"))).isEqualTo(
                LlmBudgetVerdict.EXHAUSTED
            );
        }

        @Test
        void withinBudgetButWithAnUnpricedEventIsUnverifiable() {
            assertThat(LlmBudgetService.verdictFor(new BigDecimal("5.00"), true, new BigDecimal("10.00"))).isEqualTo(
                LlmBudgetVerdict.UNVERIFIABLE
            );
        }

        @Test
        void exhaustedTakesPriorityOverUnverifiable() {
            // Both conditions true at once: already-reached-the-cap is the more actionable signal.
            assertThat(LlmBudgetService.verdictFor(new BigDecimal("10.00"), true, new BigDecimal("10.00"))).isEqualTo(
                LlmBudgetVerdict.EXHAUSTED
            );
        }

        @Test
        void uncappedWorkspaceCanNeverBeExhaustedButCanBeUnverifiable() {
            assertThat(LlmBudgetService.verdictFor(new BigDecimal("999999.00"), false, null)).isEqualTo(
                LlmBudgetVerdict.WITHIN
            );
            assertThat(LlmBudgetService.verdictFor(new BigDecimal("999999.00"), true, null)).isEqualTo(
                LlmBudgetVerdict.UNVERIFIABLE
            );
        }
    }

    @Nested
    class MonthWindow {

        @Test
        void windowIsHalfOpenUtcCalendarMonth() {
            LlmBudgetService.MonthWindow window = LlmBudgetService.MonthWindow.of(YearMonth.of(2026, 7));

            assertThat(window.from()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
            assertThat(window.to()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
        }

        @Test
        void windowRollsAcrossYearBoundary() {
            LlmBudgetService.MonthWindow window = LlmBudgetService.MonthWindow.of(YearMonth.of(2026, 12));

            assertThat(window.from()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
            assertThat(window.to()).isEqualTo(Instant.parse("2027-01-01T00:00:00Z"));
        }
    }
}
