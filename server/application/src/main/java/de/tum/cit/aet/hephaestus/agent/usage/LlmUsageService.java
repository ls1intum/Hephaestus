package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.usage.fx.FxRateLookup;
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
 * Workspace-scoped side of the LLM usage ledger: the month rollup a workspace admin sees, and its own
 * cap. The cross-tenant rollup and the host's cap live on {@link LlmUsageAdminService}.
 */
@Service
public class LlmUsageService {

    private final LlmUsageEventRepository usageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final LlmBudgetService llmBudgetService;

    private final ConfigAuditPort configAudit;
    private final AgentJobRepository jobRepository;

    /** Display-only; no number it produces is ever compared against a budget. */
    private final FxRateLookup fxRateLookup;

    public LlmUsageService(
        LlmUsageEventRepository usageRepository,
        WorkspaceRepository workspaceRepository,
        LlmBudgetService llmBudgetService,
        ConfigAuditPort configAudit,
        AgentJobRepository jobRepository,
        FxRateLookup fxRateLookup
    ) {
        this.usageRepository = usageRepository;
        this.workspaceRepository = workspaceRepository;
        this.llmBudgetService = llmBudgetService;
        this.configAudit = configAudit;
        this.jobRepository = jobRepository;
        this.fxRateLookup = fxRateLookup;
    }

    @Transactional(readOnly = true)
    public WorkspaceLlmUsageReportDTO getWorkspaceReport(Long workspaceId, YearMonth month) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));
        LlmBudgetService.MonthWindow window = LlmBudgetService.MonthWindow.of(month);

        List<LlmUsageByJobTypeDTO> byJobType = usageRepository
            .aggregateByJobType(workspaceId, window.from(), window.to())
            .stream()
            .map(row ->
                new LlmUsageByJobTypeDTO(
                    LlmUsageJobType.valueOf(row.getJobType()),
                    row.getPricedTotalCostUsd(),
                    row.getByoTotalCostUsd(),
                    row.getUnpricedEventCount(),
                    row.getInputTokens(),
                    row.getOutputTokens(),
                    row.getCacheReadTokens(),
                    row.getCacheWriteTokens(),
                    row.getTotalCalls(),
                    row.getEvents()
                )
            )
            .toList();

        List<LlmUsageByDayDTO> byDay = usageRepository
            .aggregateByDay(workspaceId, window.from(), window.to())
            .stream()
            .map(row ->
                new LlmUsageByDayDTO(
                    row.getDay(),
                    row.getPricedTotalCostUsd(),
                    row.getByoTotalCostUsd(),
                    row.getUnpricedEventCount(),
                    row.getEvents()
                )
            )
            .toList();

        BigDecimal pricedTotal = usageRepository.sumCost(workspaceId, window.from(), window.to());
        BigDecimal ownProviderTotal = usageRepository.sumByoCost(workspaceId, window.from(), window.to());
        BigDecimal instanceBudget = workspace.getMonthlyLlmBudgetUsd();
        BigDecimal ownProviderBudget = workspace.getMonthlyByoLlmBudgetUsd();
        long uncosted = usageRepository.countUncosted(workspaceId, window.from(), window.to());
        LlmBudgetVerdict instanceVerdict = LlmBudgetService.verdictFor(
            pricedTotal,
            usageRepository.existsUnpricedInstanceFunded(workspaceId, window.from(), window.to()),
            instanceBudget
        );
        LlmBudgetVerdict ownProviderVerdict = LlmBudgetService.verdictFor(
            ownProviderTotal,
            usageRepository.existsUnpricedWorkspaceFunded(workspaceId, window.from(), window.to()),
            ownProviderBudget
        );
        LlmBudgetDecision decision = livePauseDecision(workspaceId, month);
        return new WorkspaceLlmUsageReportDTO(
            month.toString(),
            instanceBudget,
            ownProviderBudget,
            pricedTotal,
            ownProviderTotal,
            uncosted,
            instanceVerdict,
            ownProviderVerdict,
            decision.blocks(FundingSource.INSTANCE),
            decision.blocks(FundingSource.WORKSPACE),
            byJobType,
            byDay,
            fxRateLookup.forMonth(month).orElse(null)
        );
    }

    /**
     * The mirror of {@code LlmUsageAdminService#updateBudget} for the purse the workspace itself pays:
     * same instrument, same audit trail, and it cannot reach the host's cap.
     */
    @Transactional
    public void updateOwnProviderBudget(Long workspaceId, @Nullable BigDecimal monthlyBudgetUsd) {
        // Locked read — see LlmUsageAdminService#updateBudget: the two caps share one workspace row,
        // and Hibernate's all-columns UPDATE would otherwise let this write revert the instance
        // admin's cap (or be reverted by it).
        Workspace workspace = workspaceRepository
            .findByIdForUpdate(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));
        BigDecimal before = workspace.getMonthlyByoLlmBudgetUsd();
        workspace.setMonthlyByoLlmBudgetUsd(monthlyBudgetUsd);
        workspaceRepository.save(workspace);
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.WORKSPACE_OWN_PROVIDER_LLM_BUDGET,
                workspaceId,
                workspaceId,
                new OwnProviderLlmBudgetSnapshot(before),
                new OwnProviderLlmBudgetSnapshot(monthlyBudgetUsd)
            )
        );
        jobRepository.releaseBudgetHolds(workspaceId, Instant.now());
    }

    /** The live gate's verdict, which always evaluates against now — so a closed month pauses nothing. */
    private LlmBudgetDecision livePauseDecision(Long workspaceId, YearMonth month) {
        return month.equals(YearMonth.now(ZoneOffset.UTC))
            ? llmBudgetService.decide(workspaceId)
            : LlmBudgetDecision.ALLOWED;
    }

    /** {@code null} = uncapped. */
    public record OwnProviderLlmBudgetSnapshot(@Nullable BigDecimal monthlyBudgetUsd) implements ConfigAuditSnapshot {}
}
