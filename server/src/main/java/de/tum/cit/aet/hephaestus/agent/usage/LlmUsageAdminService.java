package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.AdminWorkspaceLlmUsageDTO;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Instance-admin side of LLM cost governance (#1368): the cross-tenant month rollup (spend
 * totals only — metadata, no tenant content) and the per-workspace budget cap write. Deliberately
 * separate from the workspace-scoped {@link LlmUsageService}: this service is
 * {@link WorkspaceAgnostic} because it reads across every tenant; access is gated upstream by
 * {@code hasAuthority('app_admin')} on {@link LlmUsageAdminController}.
 */
@Service
@WorkspaceAgnostic("Instance-admin spend rollup aggregates across all tenants (spend metadata only)")
public class LlmUsageAdminService {

    private final LlmUsageEventRepository usageRepository;
    private final WorkspaceRepository workspaceRepository;

    public LlmUsageAdminService(LlmUsageEventRepository usageRepository, WorkspaceRepository workspaceRepository) {
        this.usageRepository = usageRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public List<AdminWorkspaceLlmUsageDTO> getWorkspaceRollups(YearMonth month) {
        LlmBudgetService.MonthWindow window = LlmBudgetService.MonthWindow.of(month);
        return usageRepository
            .aggregateByWorkspace(window.from(), window.to())
            .stream()
            .map(row ->
                new AdminWorkspaceLlmUsageDTO(
                    row.getWorkspaceId(),
                    row.getWorkspaceSlug(),
                    row.getDisplayName(),
                    row.getMonthlyBudgetUsd(),
                    row.getCostUsd(),
                    row.getEvents(),
                    LlmUsageService.isOver(row.getCostUsd(), row.getMonthlyBudgetUsd())
                )
            )
            .toList();
    }

    /** Instance-admin only (see {@code Workspace#monthlyLlmBudgetUsd} for the rationale). */
    @Transactional
    public void updateBudget(Long workspaceId, @Nullable BigDecimal monthlyLlmBudgetUsd) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));
        workspace.setMonthlyLlmBudgetUsd(monthlyLlmBudgetUsd);
        workspaceRepository.save(workspace);
    }
}
