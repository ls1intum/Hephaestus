package de.tum.cit.aet.hephaestus.agent.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetBlockReason;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetDecision;
import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ReviewBudgetAdapterTest extends BaseUnitTest {
    @Test
    void shouldRefuseScansWhenTheirBudgetIsAbsentOrNonpositive() {
        var adapter = new ReviewBudgetAdapter(mock(LlmBudgetService.class));
        assertThat(adapter.canRequest(8, ReviewKind.SCAN, null)).isFalse();
        assertThat(adapter.canRequest(8, ReviewKind.SCAN, BigDecimal.ZERO)).isFalse();
        assertThat(adapter.canRequest(8, ReviewKind.SCAN, BigDecimal.ONE.negate()))
                .isFalse();
    }

    @Test
    void shouldAdmitBoundedScansAndItemRequestsWhenBothMonthlyPursesPermit() {
        var budgets = mock(LlmBudgetService.class);
        when(budgets.decide(8L)).thenReturn(LlmBudgetDecision.ALLOWED);
        var adapter = new ReviewBudgetAdapter(budgets);
        assertThat(adapter.canRequest(8, ReviewKind.SCAN, BigDecimal.ONE)).isTrue();
        assertThat(adapter.canRequest(8, ReviewKind.ITEM, null)).isTrue();
    }

    @Test
    void shouldRefuseRequestsWhenEitherUnknownFundingPurseIsBlocked() {
        var budgets = mock(LlmBudgetService.class);
        when(budgets.decide(8L))
                .thenReturn(
                        new LlmBudgetDecision(LlmBudgetBlockReason.EXHAUSTED, LlmBudgetBlockReason.NONE),
                        new LlmBudgetDecision(LlmBudgetBlockReason.NONE, LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED));
        var adapter = new ReviewBudgetAdapter(budgets);
        assertThat(adapter.canRequest(8, ReviewKind.ITEM, null)).isFalse();
        assertThat(adapter.canRequest(8, ReviewKind.SCAN, BigDecimal.ONE)).isFalse();
    }
}
