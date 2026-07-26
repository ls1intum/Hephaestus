package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly LLM budget cap evaluation. The cap is a backstop, not a hard reservation: checks read the
 * ledger directly (indexed month-window SUM, no cache), and the ledger only gains a row when an agent
 * job or mentor turn ENDS — so work that is running right now has spent nothing as far as {@link
 * #decide} is concerned. That eventual consistency is an accepted property of the design for the
 * admission gates, which only decide whether a job may START. The one gate that must also bound a run
 * already in progress — the LLM proxy — reads {@link #headroom} instead and adds the calling attempt's
 * consumed-but-unrecorded spend itself.
 *
 * <p>Budget months are calendar months in UTC. A budget of exactly 0 is an immediate pause switch;
 * {@code null} is uncapped.
 *
 * <p><b>The policy, stated once.</b> There are two purses, each judged against its own cap and never
 * summed: the host's shared models and the workspace's own provider. The host's exhausted budget must
 * not pause work the workspace pays for itself, and vice versa. An uncapped purse is never blocked. A
 * capped purse whose month is UNVERIFIABLE — some event funded from it has no resolvable price, so its
 * true spend cannot be confirmed — is blocked exactly like an exhausted one, because a cap you cannot
 * verify is not a cap.
 *
 * <p>Enforced at {@code AgentJobService.submit} (all sandboxed detection work),
 * {@code AgentJobExecutor}'s claim-time recheck, and {@code MentorChatService.runTurnInternal} (web SSE
 * and Slack turns alike).
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
     * <b>The submission gate.</b> Asks {@link #decide}'s question and also performs the block's
     * observability, so no caller has to remember to log and count a refusal.
     *
     * @return true when work funded by {@code fundingSource} is blocked; the caller simply skips the
     *     submission, since the block is already logged and counted here.
     */
    @Transactional(readOnly = true)
    public boolean blockSubmission(Workspace workspace, String jobType, @Nullable FundingSource fundingSource) {
        // Both the log word and the metric tag name the purse that ACTUALLY blocked. An unattributable
        // call is judged against both caps, so the blocking purse is not always the one the caller
        // assumed — deriving the label from the requested funding source would file a BYO block under
        // the instance cap.
        LlmBudgetDecision.Block block = headroom(workspace).decide().decideFor(fundingSource);
        if (!block.blocked()) {
            return false;
        }
        log.info(
            "Skipping agent job submission — {} monthly LLM budget {}: workspaceId={}, jobType={}",
            block.purse() == FundingSource.WORKSPACE ? "own-provider" : "shared-model",
            block.reason() == LlmBudgetBlockReason.EXHAUSTED ? "exhausted" : "unverifiable (some spend has no price)",
            workspace.getId(),
            jobType
        );
        meterRegistry.counter("llm.budget.blocked", "surface", "agent_job", "cap", capTag(block.purse())).increment();
        return true;
    }

    private static String capTag(@Nullable FundingSource purse) {
        return purse == FundingSource.WORKSPACE ? "byo" : "instance";
    }

    /**
     * <b>Is this workspace blocked, and why?</b> The verdict every gate that only decides whether work
     * may START asks — job submission, the claim-time recheck, and a mentor turn.
     */
    @Transactional(readOnly = true)
    public LlmBudgetDecision decide(Long workspaceId) {
        return headroom(workspaceId).decide();
    }

    /**
     * <b>How much room is left, as numbers?</b> The same month-window reads as {@link #decide}, handed
     * back before they become a verdict, so a caller that can see spend the ledger cannot — today only
     * the LLM proxy, which knows the calling attempt's already-consumed tokens — can add it before the
     * cap comparison. Every verdict this class produces comes from this one read, so the gate that
     * pauses work and the number an admin is shown can never come from different arithmetic.
     */
    @Transactional(readOnly = true)
    public LlmBudgetHeadroom headroom(Long workspaceId) {
        return workspaceRepository.findById(workspaceId).map(this::headroom).orElse(LlmBudgetHeadroom.UNCAPPED);
    }

    private LlmBudgetHeadroom headroom(Workspace workspace) {
        return headroom(workspace.getId(), workspace.getMonthlyLlmBudgetUsd(), workspace.getMonthlyByoLlmBudgetUsd());
    }

    private LlmBudgetHeadroom headroom(
        Long workspaceId,
        @Nullable BigDecimal instanceBudgetUsd,
        @Nullable BigDecimal byoBudgetUsd
    ) {
        MonthWindow window = MonthWindow.of(YearMonth.now(ZoneOffset.UTC));
        BigDecimal instanceSpent = spentIfCapped(instanceBudgetUsd, () ->
            usageRepository.sumCost(workspaceId, window.from(), window.to())
        );
        BigDecimal byoSpent = spentIfCapped(byoBudgetUsd, () ->
            usageRepository.sumByoCost(workspaceId, window.from(), window.to())
        );
        return new LlmBudgetHeadroom(
            instanceSpent,
            instanceBudgetUsd,
            probeUnpriced(instanceSpent, instanceBudgetUsd, () ->
                usageRepository.existsUnpricedInstanceFunded(workspaceId, window.from(), window.to())
            ),
            byoSpent,
            byoBudgetUsd,
            probeUnpriced(byoSpent, byoBudgetUsd, () ->
                usageRepository.existsUnpricedWorkspaceFunded(workspaceId, window.from(), window.to())
            )
        );
    }

    /** An uncapped purse is never blocked, so its month-window SUM is never run. */
    private static @Nullable BigDecimal spentIfCapped(@Nullable BigDecimal budgetUsd, Supplier<BigDecimal> pricedCost) {
        return budgetUsd == null ? null : pricedCost.get();
    }

    /**
     * A purse already EXHAUSTED on recorded spend alone never runs the unpriced probe: EXHAUSTED
     * outranks UNVERIFIABLE, and adding in-flight spend can only keep it exhausted.
     */
    private static boolean probeUnpriced(
        @Nullable BigDecimal spentUsd,
        @Nullable BigDecimal budgetUsd,
        BooleanSupplier hasUnpriced
    ) {
        if (spentUsd == null || budgetUsd == null || capReached(spentUsd, budgetUsd)) {
            return false;
        }
        return hasUnpriced.getAsBoolean();
    }

    /**
     * <b>The cap comparison, written once.</b> Both the live gate ({@link LlmBudgetHeadroom#decideWith},
     * which pauses work) and the reported verdict ({@link #verdictFor}, which tells the admin why) ask
     * this one question, so the number a workspace sees can never disagree with the number that paused
     * it.
     *
     * <p>Inclusive: reaching the cap exhausts it. That makes a cap of exactly 0 an immediate pause
     * switch, which is the only reading under which "set the budget to zero" stops anything.
     *
     * @param confirmedSpendUsd this window's priced spend from the purse being judged
     * @param budgetUsd that purse's cap; {@code null} = uncapped, never reached
     */
    static boolean capReached(BigDecimal confirmedSpendUsd, @Nullable BigDecimal budgetUsd) {
        return budgetUsd != null && confirmedSpendUsd.compareTo(budgetUsd) >= 0;
    }

    /**
     * <b>What does a month look like, given its numbers?</b> The one entry point that takes the spend
     * instead of reading it, for the two rollups that already have it from a GROUP BY — the
     * workspace-scoped and instance-admin usage reports. {@code EXHAUSTED} outranks
     * {@code UNVERIFIABLE}: both pause a capped purse, and a month already over its cap on confirmed
     * spend alone names the reason an admin can act on, so the softer wording only surfaces while the
     * ledger still cannot rule EXHAUSTED out.
     *
     * @param confirmedSpendUsd this window's confirmed spend from the purse being judged
     * @param hasUnpricedEvent whether an event funded from that purse this window has no resolvable price
     * @param monthlyBudgetUsd that purse's cap; {@code null} = uncapped (never EXHAUSTED, and
     *     UNVERIFIABLE there is a data-quality note only — an uncapped purse is never paused)
     */
    public static LlmBudgetVerdict verdictFor(
        BigDecimal confirmedSpendUsd,
        boolean hasUnpricedEvent,
        @Nullable BigDecimal monthlyBudgetUsd
    ) {
        if (capReached(confirmedSpendUsd, monthlyBudgetUsd)) {
            return LlmBudgetVerdict.EXHAUSTED;
        }
        if (hasUnpricedEvent) {
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
