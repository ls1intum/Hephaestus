package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.LlmUsageByDayDTO;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.LlmUsageByJobTypeDTO;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageDTOs.WorkspaceLlmUsageReportDTO;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
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

    public LlmUsageService(LlmUsageEventRepository usageRepository, WorkspaceRepository workspaceRepository) {
        this.usageRepository = usageRepository;
        this.workspaceRepository = workspaceRepository;
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
        BigDecimal budget = workspace.getMonthlyLlmBudgetUsd();
        long uncosted = usageRepository.countUncosted(workspaceId, window.from(), window.to());
        boolean hasUnpricedInstanceEvent = usageRepository.existsUnpricedInstanceFunded(
            workspaceId,
            window.from(),
            window.to()
        );
        LlmBudgetVerdict verdict = LlmBudgetService.verdictFor(pricedTotal, hasUnpricedInstanceEvent, budget);
        return new WorkspaceLlmUsageReportDTO(
            month.toString(),
            budget,
            pricedTotal,
            byoTotal,
            uncosted,
            verdict,
            byJobType,
            byDay
        );
    }
}
