package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.LlmUsageByDayDTO;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.LlmUsageByJobTypeDTO;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.WorkspaceLlmUsageReportDTO;
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
 * Workspace-scoped read-side of the LLM usage ledger (#1368): the month rollup a workspace
 * admin sees. The cross-tenant admin rollup + budget write live on {@link LlmUsageAdminService}.
 */
@Service
public class LlmUsageService {

    private final LlmUsageEventRepository usageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final LlmBudgetService llmBudgetService;

    private final ConfigAuditPort configAudit;
    private final AgentJobRepository jobRepository;

    /**
     * Display-only: the rate attached to the response so a UI can show a euro estimate beside the
     * USD figures. Read-side only — no number it produces is ever compared against a budget.
     */
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
        BigDecimal byoTotal = usageRepository.sumByoCost(workspaceId, window.from(), window.to());
        BigDecimal instanceBudget = workspace.getMonthlyLlmBudgetUsd();
        BigDecimal byoBudget = workspace.getMonthlyByoLlmBudgetUsd();
        long uncosted = usageRepository.countUncosted(workspaceId, window.from(), window.to());
        // Each purse is judged only against blind spots its own owner can clear: an unpriced shared
        // model is the host's to price, an unpriced BYO model the workspace's.
        LlmBudgetVerdict instanceVerdict = LlmBudgetService.verdictFor(
            pricedTotal,
            usageRepository.existsUnpricedInstanceFunded(workspaceId, window.from(), window.to()),
            instanceBudget
        );
        LlmBudgetVerdict byoVerdict = LlmBudgetService.verdictFor(
            byoTotal,
            usageRepository.existsUnpricedWorkspaceFunded(workspaceId, window.from(), window.to()),
            byoBudget
        );
        // The paused flags mirror LlmBudgetService's decision — the SAME live gate that
        // AgentJobService.submit / the claim-time recheck / MentorChatService actually enforce, rather
        // than something the webapp re-derives. Only meaningful for the CURRENT month: the decision
        // always evaluates against "now", so reporting it for a past month would misleadingly imply a
        // closed month is still pausing new work.
        boolean isCurrentMonth = month.equals(YearMonth.now(ZoneOffset.UTC));
        LlmBudgetDecision decision = isCurrentMonth ? llmBudgetService.decide(workspace) : LlmBudgetDecision.ALLOWED;
        return new WorkspaceLlmUsageReportDTO(
            month.toString(),
            instanceBudget,
            byoBudget,
            pricedTotal,
            byoTotal,
            uncosted,
            instanceVerdict,
            byoVerdict,
            decision.blocks(FundingSource.INSTANCE),
            decision.blocks(FundingSource.WORKSPACE),
            byJobType,
            byDay,
            fxRateLookup.forMonth(month).orElse(null)
        );
    }

    /**
     * Set or clear this workspace's own monthly cap on its own-provider (BYO) spend (#1368).
     *
     * <p>The workspace-side mirror of {@code LlmUsageAdminService#updateBudget}: same instrument,
     * same audit trail, different purse. A workspace admin owns this one because it governs money the
     * workspace pays; it cannot touch the host's cap, so nothing here can loosen the instance's
     * protection — only the workspace's own spending is affected.
     *
     * <p>Audited, and it releases any jobs the claim loop is holding on this cap so raising it takes
     * effect on the next poll rather than up to an hour later.
     */
    @Transactional
    public void updateByoBudget(Long workspaceId, @Nullable BigDecimal monthlyByoLlmBudgetUsd) {
        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceId.toString()));
        BigDecimal before = workspace.getMonthlyByoLlmBudgetUsd();
        workspace.setMonthlyByoLlmBudgetUsd(monthlyByoLlmBudgetUsd);
        workspaceRepository.save(workspace);
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.WORKSPACE_BYO_LLM_BUDGET,
                workspaceId,
                workspaceId,
                new ByoLlmBudgetSnapshot(before),
                new ByoLlmBudgetSnapshot(monthlyByoLlmBudgetUsd)
            )
        );
        jobRepository.releaseBudgetHolds(workspaceId, Instant.now());
    }

    /** The cap itself — a plain amount, no credential material. {@code null} = uncapped. */
    public record ByoLlmBudgetSnapshot(@Nullable BigDecimal monthlyByoLlmBudgetUsd) implements ConfigAuditSnapshot {}
}
