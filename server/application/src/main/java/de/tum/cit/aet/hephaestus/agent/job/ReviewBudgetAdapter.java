package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.usage.LlmBudgetService;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBudget;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnServerRole
@RequiredArgsConstructor
public class ReviewBudgetAdapter implements ReviewBudget {
    private final LlmBudgetService budgets;

    @Override
    public boolean canRequest(long workspaceId, ReviewKind kind, @Nullable BigDecimal budgetUsd) {
        if (kind == ReviewKind.SCAN && (budgetUsd == null || budgetUsd.signum() <= 0)) return false;
        // Request admission has no frozen funding source. The budget service checks both purses until it is known.
        return !budgets.decide(workspaceId).blocks(null);
    }
}
