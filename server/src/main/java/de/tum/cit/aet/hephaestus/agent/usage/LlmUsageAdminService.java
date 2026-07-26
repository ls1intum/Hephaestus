package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.usage.fx.FxRateInfoDTO;
import de.tum.cit.aet.hephaestus.agent.usage.fx.FxRateLookup;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Instance-admin side of LLM cost governance: the cross-tenant month rollup (spend
 * totals only — metadata, no tenant content) and the per-workspace budget cap write. Deliberately
 * separate from the workspace-scoped {@link LlmUsageService}. Access is gated upstream by
 * {@code hasAuthority('app_admin')} on {@link LlmUsageAdminController}.
 *
 * <p>The {@link WorkspaceAgnostic} bypass is declared on {@link #getReport} alone, not on the class:
 * only the rollup genuinely reads across every tenant. {@link #updateBudget} writes exactly one
 * workspace's row, so it stays inside the tenancy inspector where a stray cross-tenant write is
 * still an error.
 */
@Service
public class LlmUsageAdminService {

    private final LlmUsageEventRepository usageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ConfigAuditPort configAudit;
    private final AgentJobRepository jobRepository;

    /** Display-only (see {@link LlmUsageService}); never an input to a cap comparison. */
    private final FxRateLookup fxRateLookup;

    public LlmUsageAdminService(
        LlmUsageEventRepository usageRepository,
        WorkspaceRepository workspaceRepository,
        ConfigAuditPort configAudit,
        AgentJobRepository jobRepository,
        FxRateLookup fxRateLookup
    ) {
        this.usageRepository = usageRepository;
        this.workspaceRepository = workspaceRepository;
        this.configAudit = configAudit;
        this.jobRepository = jobRepository;
        this.fxRateLookup = fxRateLookup;
    }

    @Transactional(readOnly = true)
    @WorkspaceAgnostic("Instance-admin spend rollup aggregates across all tenants (spend metadata only)")
    public AdminLlmUsageReportDTO getReport(YearMonth month) {
        LlmBudgetService.MonthWindow window = LlmBudgetService.MonthWindow.of(month);
        boolean isCurrentMonth = month.equals(YearMonth.now(ZoneOffset.UTC));
        // Resolved once for the whole response and reported once, on the envelope: one month has
        // exactly one rate, so a per-row copy was N duplicates of a single request-level fact.
        FxRateInfoDTO fx = fxRateLookup.forMonth(month).orElse(null);
        List<AdminWorkspaceLlmUsageDTO> workspaces = usageRepository
            .aggregateByWorkspace(window.from(), window.to())
            .stream()
            .map(row -> toRollup(row, isCurrentMonth))
            .toList();
        return new AdminLlmUsageReportDTO(month.toString(), fx, workspaces);
    }

    /**
     * Instance-admin only (see {@code Workspace#monthlyLlmBudgetUsd} for the rationale).
     *
     * <p>Audited: raising a cap is what lets a workspace keep spending, so "who changed it, when,
     * from what to what" belongs on the config trail. The audit write joins this transaction — if it
     * fails, the cap change rolls back with it rather than committing untracked.
     */
    @Transactional
    public void updateBudget(String workspaceSlug, @Nullable BigDecimal monthlyLlmBudgetUsd) {
        // Locked read: this cap and the workspace's own-provider cap are two columns of ONE workspace
        // row, written by two different people. Hibernate's UPDATE covers every column, so an instance
        // admin and a workspace admin patching concurrently would each revert the other's cap — and
        // both audit trails would claim a transition that no longer holds. The lock makes
        // read-snapshot-write one step, so the two purses stay independent.
        Workspace workspace = workspaceRepository
            .findByWorkspaceSlugForUpdate(workspaceSlug)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceSlug));
        BigDecimal before = workspace.getMonthlyLlmBudgetUsd();
        workspace.setMonthlyLlmBudgetUsd(monthlyLlmBudgetUsd);
        workspaceRepository.save(workspace);
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.WORKSPACE_INSTANCE_LLM_BUDGET,
                workspace.getId(),
                workspace.getId(),
                new LlmBudgetSnapshot(before),
                new LlmBudgetSnapshot(monthlyLlmBudgetUsd)
            )
        );
        // Raising the cap must take effect now, not up to an hour from now: jobs the claim loop held
        // on this budget are released so the next poll can pick them up. Harmless when the cap was
        // lowered instead — a released job is simply re-held on its next claim attempt.
        jobRepository.releaseBudgetHolds(workspace.getId(), Instant.now());
    }

    /**
     * One workspace's row, with each purse judged against its own cap. The paused flags are only
     * meaningful for the month in progress — a closed month cannot be pausing anything today, and
     * reporting otherwise would send an admin chasing a block that no longer exists.
     */
    private static AdminWorkspaceLlmUsageDTO toRollup(
        LlmUsageEventRepository.WorkspaceAggregate row,
        boolean isCurrentMonth
    ) {
        LlmBudgetVerdict instanceVerdict = LlmBudgetService.verdictFor(
            row.getPricedTotalCostUsd(),
            row.isHasUnpricedInstanceUsage(),
            row.getMonthlyBudgetUsd()
        );
        LlmBudgetVerdict ownProviderVerdict = LlmBudgetService.verdictFor(
            row.getByoTotalCostUsd(),
            row.isHasUnpricedByoUsage(),
            row.getByoMonthlyBudgetUsd()
        );
        return new AdminWorkspaceLlmUsageDTO(
            row.getWorkspaceSlug(),
            row.getDisplayName(),
            row.getMonthlyBudgetUsd(),
            row.getByoMonthlyBudgetUsd(),
            row.getPricedTotalCostUsd(),
            row.getByoTotalCostUsd(),
            row.getEvents(),
            instanceVerdict,
            ownProviderVerdict,
            isCurrentMonth && instanceVerdict != LlmBudgetVerdict.WITHIN,
            isCurrentMonth && ownProviderVerdict != LlmBudgetVerdict.WITHIN
        );
    }

    /** The cap itself — a plain amount, no credential or contact material. {@code null} = uncapped. */
    public record LlmBudgetSnapshot(@Nullable BigDecimal monthlyLlmBudgetUsd) implements ConfigAuditSnapshot {}
}
