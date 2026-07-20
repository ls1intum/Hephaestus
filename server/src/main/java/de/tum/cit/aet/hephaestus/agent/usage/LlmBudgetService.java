package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly LLM budget cap evaluation (#1368). The cap is a per-workspace backstop against a
 * misbehaving workspace or sync burst quietly burning the instance budget — not a hard
 * reservation: checks read the ledger directly (indexed month-window SUM, no cache), so
 * in-flight work that hasn't been costed yet can overshoot slightly. That eventual consistency
 * is an accepted property of the design.
 *
 * <p>Budget months are calendar months in UTC. A budget of exactly 0 acts as an immediate
 * pause switch; {@code null} means uncapped.
 *
 * <p>Enforcement points (the "pause" mechanism):
 * <ul>
 *   <li>{@code AgentJobService.submit} — the single choke point for all sandboxed detection
 *       work: webhook-triggered detection, retrospective replays, dev/bot manual triggers, and
 *       conversation reviews.</li>
 *   <li>{@code MentorChatService.runTurnInternal} — transport-neutral mentor gate covering web
 *       SSE and Slack turns, checked before the turn persists anything.</li>
 * </ul>
 */
@Service
public class LlmBudgetService {

    private static final Logger log = LoggerFactory.getLogger(LlmBudgetService.class);

    private final LlmUsageEventRepository usageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final MeterRegistry meterRegistry;

    public LlmBudgetService(
        LlmUsageEventRepository usageRepository,
        WorkspaceRepository workspaceRepository,
        MeterRegistry meterRegistry
    ) {
        this.usageRepository = usageRepository;
        this.workspaceRepository = workspaceRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Submission-side gate: true when the workspace's cap is reached, in which case the block is
     * logged and counted ({@code llm.budget.blocked}, surface {@code agent_job}) so operators can
     * see suppressed work. Callers just skip the submission.
     */
    @Transactional(readOnly = true)
    public boolean blockSubmission(Workspace workspace, String jobType) {
        if (!isBudgetExhausted(workspace)) {
            return false;
        }
        log.info(
            "Skipping agent job submission — monthly LLM budget exhausted: workspaceId={}, jobType={}",
            workspace.getId(),
            jobType
        );
        meterRegistry.counter("llm.budget.blocked", "surface", "agent_job").increment();
        return true;
    }

    /** True when the workspace has a cap and current-month spend has reached it. */
    @Transactional(readOnly = true)
    public boolean isBudgetExhausted(Workspace workspace) {
        return isBudgetExhausted(workspace.getId(), workspace.getMonthlyLlmBudgetUsd());
    }

    /** Overload for callers holding only the id (mentor gate). Unknown workspace = not exhausted. */
    @Transactional(readOnly = true)
    public boolean isBudgetExhausted(Long workspaceId) {
        return workspaceRepository.findById(workspaceId).map(this::isBudgetExhausted).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isBudgetExhausted(Long workspaceId, @Nullable BigDecimal monthlyBudgetUsd) {
        if (monthlyBudgetUsd == null) {
            return false;
        }
        return monthToDateCost(workspaceId).compareTo(monthlyBudgetUsd) >= 0;
    }

    /** Current UTC-calendar-month spend for the workspace (null ledger costs count as zero). */
    @Transactional(readOnly = true)
    public BigDecimal monthToDateCost(Long workspaceId) {
        MonthWindow window = MonthWindow.of(YearMonth.now(ZoneOffset.UTC));
        return usageRepository.sumCost(workspaceId, window.from(), window.to());
    }

    /**
     * The month's budget verdict (#1368 slice 6) — a pure function shared by the workspace-scoped
     * and instance-admin usage rollups so the rule lives in exactly one place.
     *
     * <p>{@code EXHAUSTED} takes priority over {@code UNVERIFIABLE}: a workspace that has already
     * reached its cap on confirmed spend alone doesn't need the softer "some usage is unpriced"
     * warning — it's already paused. {@code UNVERIFIABLE} only surfaces when the ledger cannot yet
     * rule out EXHAUSTED being the true state.
     *
     * @param pricedInstanceCostUsd this window's confirmed (instance-funded, priced) spend — the
     *     same figure {@link #monthToDateCost} and {@code LlmUsageEventRepository#sumCost} compute
     * @param hasUnpricedInstanceEvent whether an instance-funded event this window has no
     *     resolvable price ({@code LlmUsageEventRepository#existsUnpricedInstanceFunded})
     * @param monthlyBudgetUsd the workspace's cap; {@code null} = uncapped (never EXHAUSTED)
     */
    public static LlmBudgetVerdict verdictFor(
        BigDecimal pricedInstanceCostUsd,
        boolean hasUnpricedInstanceEvent,
        @Nullable BigDecimal monthlyBudgetUsd
    ) {
        if (monthlyBudgetUsd != null && pricedInstanceCostUsd.compareTo(monthlyBudgetUsd) >= 0) {
            return LlmBudgetVerdict.EXHAUSTED;
        }
        if (hasUnpricedInstanceEvent) {
            return LlmBudgetVerdict.UNVERIFIABLE;
        }
        return LlmBudgetVerdict.WITHIN;
    }

    /** Half-open UTC instant window [from, to) of one calendar month. */
    public record MonthWindow(Instant from, Instant to) {
        public static MonthWindow of(YearMonth month) {
            return new MonthWindow(
                month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()
            );
        }
    }
}
