package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.AdminWorkspaceLlmUsageDTO;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
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
    private final ConfigAuditPort configAudit;

    public LlmUsageAdminService(
        LlmUsageEventRepository usageRepository,
        WorkspaceRepository workspaceRepository,
        ConfigAuditPort configAudit
    ) {
        this.usageRepository = usageRepository;
        this.workspaceRepository = workspaceRepository;
        this.configAudit = configAudit;
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
                    row.getPricedTotalCostUsd(),
                    row.getByoTotalCostUsd(),
                    row.getEvents(),
                    LlmBudgetService.verdictFor(
                        row.getPricedTotalCostUsd(),
                        row.isHasUnpricedInstanceUsage(),
                        row.getMonthlyBudgetUsd()
                    )
                )
            )
            .toList();
    }

    /**
     * Instance-admin only (see {@code Workspace#monthlyLlmBudgetUsd} for the rationale).
     *
     * <p>Audited: raising a cap is what lets a workspace keep spending, so "who changed it, when,
     * from what to what" belongs on the config trail. The audit write joins this transaction — if it
     * fails, the cap change rolls back with it rather than committing untracked.
     */
    @Transactional
    public void updateBudget(Long workspaceId, @Nullable BigDecimal monthlyLlmBudgetUsd) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));
        BigDecimal before = workspace.getMonthlyLlmBudgetUsd();
        workspace.setMonthlyLlmBudgetUsd(monthlyLlmBudgetUsd);
        workspaceRepository.save(workspace);
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.WORKSPACE_LLM_BUDGET,
                workspace.getId(),
                workspace.getId(),
                new LlmBudgetSnapshot(before),
                new LlmBudgetSnapshot(monthlyLlmBudgetUsd)
            )
        );
    }

    /** The cap itself — a plain amount, no credential or contact material. {@code null} = uncapped. */
    public record LlmBudgetSnapshot(@Nullable BigDecimal monthlyLlmBudgetUsd) implements ConfigAuditSnapshot {}
}
