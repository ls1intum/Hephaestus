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
 *   <li>{@code AgentJobExecutor}'s claim-time recheck (#1368 fix wave) — a workspace can pre-queue
 *       jobs faster than the cap updates; this second check refuses a job whose workspace has
 *       since crossed the cap instead of letting every pre-queued job run once the gate has
 *       already closed.</li>
 *   <li>{@code MentorChatService.runTurnInternal} — transport-neutral mentor gate covering web
 *       SSE and Slack turns, checked before the turn persists anything.</li>
 * </ul>
 *
 * <p>{@link #blockReason} also blocks a workspace whose month is
 * {@link LlmBudgetVerdict#UNVERIFIABLE} — a budget is set, but at least one instance-funded event
 * has no resolvable price, so the true spend cannot be confirmed against the cap. A cap you cannot
 * verify is not a cap, so an unverifiable month on a capped workspace is blocked exactly like
 * {@link LlmBudgetVerdict#EXHAUSTED}. An uncapped workspace is never blocked either way (it opted
 * out of enforcement); {@link #isBudgetExhausted} stays EXHAUSTED-only for callers that want the
 * confirmed-spend signal alone.
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
     * Submission-side gate: true when the workspace is blocked (cap reached, or an UNVERIFIABLE
     * month under {@code defaultUnpricedPolicy=BLOCK} — see {@link #blockReason}), in which case
     * the block is logged and counted ({@code llm.budget.blocked}, surface {@code agent_job}) so
     * operators can see suppressed work. Callers just skip the submission.
     */
    @Transactional(readOnly = true)
    public boolean blockSubmission(Workspace workspace, String jobType) {
        LlmBudgetBlockReason reason = blockReason(workspace);
        if (reason == LlmBudgetBlockReason.NONE) {
            return false;
        }
        log.info(
            "Skipping agent job submission — monthly LLM budget {}: workspaceId={}, jobType={}",
            reason == LlmBudgetBlockReason.EXHAUSTED ? "exhausted" : "unverifiable (blocked by instance policy)",
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
     * The blocking verdict for a workspace (#1368 fix wave) — the one decision all three
     * enforcement gates share. See the class doc for how this differs from
     * {@link #isBudgetExhausted}.
     */
    @Transactional(readOnly = true)
    public LlmBudgetBlockReason blockReason(Workspace workspace) {
        return blockReason(workspace.getId(), workspace.getMonthlyLlmBudgetUsd());
    }

    /** Overload for callers holding only the id (mentor gate, claim-time recheck). */
    @Transactional(readOnly = true)
    public LlmBudgetBlockReason blockReason(Long workspaceId) {
        return workspaceRepository
            .findById(workspaceId)
            .map(w -> blockReason(w.getId(), w.getMonthlyLlmBudgetUsd()))
            .orElse(LlmBudgetBlockReason.NONE);
    }

    private LlmBudgetBlockReason blockReason(Long workspaceId, @Nullable BigDecimal monthlyBudgetUsd) {
        if (monthlyBudgetUsd == null) {
            return LlmBudgetBlockReason.NONE; // uncapped = never blocked, either reason
        }
        // Reached only for a capped workspace (uncapped returned NONE above): an unverifiable month
        // is blocked just like an exhausted one — a cap whose true spend can't be confirmed is not a
        // cap. Uncapped workspaces opted out of enforcement and are never blocked for either reason.
        return switch (currentVerdict(workspaceId, monthlyBudgetUsd)) {
            case EXHAUSTED -> LlmBudgetBlockReason.EXHAUSTED;
            case UNVERIFIABLE -> LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED;
            case WITHIN -> LlmBudgetBlockReason.NONE;
        };
    }

    /**
     * This month's verdict for one workspace. Short-circuits before the unpriced-event query when
     * the priced sum alone already proves EXHAUSTED — no need to know about unpriced usage once the
     * confirmed spend has already crossed the cap.
     */
    private LlmBudgetVerdict currentVerdict(Long workspaceId, BigDecimal monthlyBudgetUsd) {
        MonthWindow window = MonthWindow.of(YearMonth.now(ZoneOffset.UTC));
        BigDecimal pricedCost = usageRepository.sumCost(workspaceId, window.from(), window.to());
        if (pricedCost.compareTo(monthlyBudgetUsd) >= 0) {
            return LlmBudgetVerdict.EXHAUSTED;
        }
        boolean hasUnpriced = usageRepository.existsUnpricedInstanceFunded(workspaceId, window.from(), window.to());
        return verdictFor(pricedCost, hasUnpriced, monthlyBudgetUsd);
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
