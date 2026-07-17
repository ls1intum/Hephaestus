package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.pricing.ModelPricingService;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sole writer of the {@code llm_usage_event} ledger (#1368). Called by the two LLM spend
 * sources — {@code AgentJobExecutor} (detection / replay jobs) and {@code MentorTurnPersistence}
 * (mentor turns) — right after they commit their own result.
 *
 * <p>Accounting must never break the product path: each record runs in its own
 * {@code REQUIRES_NEW} transaction and every failure is caught and logged. The worst case is a
 * missed ledger row (slight budget under-count), which the eventually-consistent budget cap
 * tolerates by design. Only a {@code ux_llm_usage_event_source} violation is benign (the same
 * source billed twice — first write wins); every other integrity failure is a real accounting
 * loss and goes out loudly on {@code llm.usage.record.failure}.
 *
 * <p>This is also the single choke point where a cost is resolved and sanity-checked, so the
 * two writers cannot drift:
 * <ul>
 *   <li>Caller-supplied cost wins (Pi computes it with the provider price table baked in, so it
 *       covers models our registry doesn't know).</li>
 *   <li>Absent/implausible cost falls back to {@link ModelPricingService}.</li>
 *   <li>Still unknown = the ledger stores NULL and, for a <em>capped</em> workspace, we WARN +
 *       count {@code llm.usage.uncosted}: tokens burned that the cap cannot see are exactly the
 *       blind spot this feature exists to remove.</li>
 * </ul>
 *
 * <p>Budget-crossing alert: if the workspace has a monthly cap and this event pushed
 * month-to-date spend across it, emit a WARN (Sentry-visible) plus the
 * {@code llm.budget.exhausted} counter — the single "you are now paused" operator signal.
 */
@Service
public class LlmUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageRecorder.class);

    /** Name of the ledger's source_id unique index — the one integrity failure that is benign. */
    private static final String SOURCE_UNIQUE_CONSTRAINT = "ux_llm_usage_event_source";

    /**
     * ≈10× a worst-case frontier-model unit of work. A Pi-side regression reporting {@code -50}
     * or {@code 1e9} would otherwise flow straight into the budget SUM — a negative would
     * <em>un-pause</em> an exhausted workspace, and a huge value overflows {@code NUMERIC(12,6)}.
     * Implausible values are treated as "cost unknown" and re-derived from the pricing table.
     */
    private static final BigDecimal COST_USD_SANITY_CAP = BigDecimal.valueOf(100);

    private final LlmUsageEventRepository usageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final LlmBudgetService budgetService;
    private final ModelPricingService pricingService;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate requiresNewTx;

    public LlmUsageRecorder(
        LlmUsageEventRepository usageRepository,
        WorkspaceRepository workspaceRepository,
        LlmBudgetService budgetService,
        ModelPricingService pricingService,
        MeterRegistry meterRegistry,
        PlatformTransactionManager transactionManager
    ) {
        this.usageRepository = usageRepository;
        this.workspaceRepository = workspaceRepository;
        this.budgetService = budgetService;
        this.pricingService = pricingService;
        this.meterRegistry = meterRegistry;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** One unit of LLM work to append to the ledger. Token counts clamp negative/null to zero at the call site. */
    public record LlmUsageSample(
        LlmUsageJobType jobType,
        UUID sourceId,
        @Nullable String model,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        int totalCalls,
        @Nullable BigDecimal costUsd,
        Instant occurredAt
    ) {}

    /**
     * Append one ledger row. Never throws — see class doc. MUST be called AFTER the caller's own
     * transaction commits: the recorder's {@code REQUIRES_NEW} would otherwise hold a second pool
     * connection under the caller's, and a caller that rolls back would leave a billed row behind.
     */
    public void record(Long workspaceId, LlmUsageSample sample) {
        try {
            requiresNewTx.executeWithoutResult(tx -> {
                Workspace workspace = workspaceRepository.getReferenceById(workspaceId);
                BigDecimal costUsd = resolveCostUsd(workspaceId, workspace.getMonthlyLlmBudgetUsd(), sample);
                LlmUsageEvent event = new LlmUsageEvent();
                event.setId(UUID.randomUUID());
                event.setWorkspace(workspace);
                event.setJobType(sample.jobType());
                event.setSourceId(sample.sourceId());
                event.setModel(sample.model());
                event.setInputTokens(Math.max(0, sample.inputTokens()));
                event.setOutputTokens(Math.max(0, sample.outputTokens()));
                event.setCacheReadTokens(Math.max(0, sample.cacheReadTokens()));
                event.setCacheWriteTokens(Math.max(0, sample.cacheWriteTokens()));
                event.setTotalCalls(Math.max(1, sample.totalCalls()));
                event.setCostUsd(costUsd);
                event.setOccurredAt(sample.occurredAt());
                usageRepository.saveAndFlush(event);
                alertIfBudgetCrossed(workspaceId, workspace.getMonthlyLlmBudgetUsd(), costUsd);
            });
        } catch (DataIntegrityViolationException e) {
            if (isSourceAlreadyBilled(e)) {
                // The same source billed twice (e.g. a mentor finalise-vs-interrupt race, or a job
                // re-run under the same id). First write wins — by design, not an error.
                log.debug("LLM usage already recorded for sourceId={} — skipping duplicate", sample.sourceId());
                return;
            }
            recordFailed(workspaceId, sample, e);
        } catch (RuntimeException e) {
            recordFailed(workspaceId, sample, e);
        }
    }

    /**
     * The cost this event contributes to the budget: caller value if plausible, else the pricing
     * table, else NULL (unknown — see the class doc's blind-spot note).
     */
    @Nullable
    private BigDecimal resolveCostUsd(Long workspaceId, @Nullable BigDecimal budget, LlmUsageSample sample) {
        BigDecimal reported = sample.costUsd();
        if (reported != null) {
            if (reported.signum() >= 0 && reported.compareTo(COST_USD_SANITY_CAP) <= 0) {
                return reported;
            }
            log.warn(
                "Implausible LLM cost reported, re-deriving from the pricing table: workspaceId={}, jobType={}, " +
                    "sourceId={}, reportedCostUsd={}",
                workspaceId,
                sample.jobType(),
                sample.sourceId(),
                reported
            );
        }
        BigDecimal derived =
            sample.model() != null
                ? pricingService
                      .computeCost(
                          sample.model(),
                          Math.max(0, sample.inputTokens()),
                          Math.max(0, sample.outputTokens()),
                          Math.max(0, sample.cacheReadTokens()),
                          Math.max(0, sample.cacheWriteTokens())
                      )
                      .orElse(null)
                : null;
        if (derived == null) {
            warnUncosted(workspaceId, budget, sample);
        }
        return derived;
    }

    /**
     * Tokens burned with no resolvable cost contribute zero to the month SUM, so a capped
     * workspace could in principle spend past its cap unpaused. Loud for capped workspaces (the
     * cap is the thing being undermined); DEBUG otherwise. The report surfaces the same count as
     * {@code uncostedEvents} so an admin sees the gap without reading logs.
     */
    private void warnUncosted(Long workspaceId, @Nullable BigDecimal budget, LlmUsageSample sample) {
        meterRegistry.counter("llm.usage.uncosted").increment();
        if (budget == null) {
            log.debug(
                "LLM usage with no resolvable cost (workspace uncapped): workspaceId={}, model={}",
                workspaceId,
                sample.model()
            );
            return;
        }
        log.warn(
            "LLM usage with no resolvable cost for a BUDGETED workspace — these tokens are invisible to the " +
                "monthly cap. Add a model_pricing row for the model: workspaceId={}, model={}, jobType={}, " +
                "inputTokens={}, outputTokens={}",
            workspaceId,
            sample.model(),
            sample.jobType(),
            sample.inputTokens(),
            sample.outputTokens()
        );
    }

    private void recordFailed(Long workspaceId, LlmUsageSample sample, RuntimeException e) {
        log.error(
            "LLM usage ledger write failed (spend under-counted until fixed): workspaceId={}, jobType={}, sourceId={}: {}",
            workspaceId,
            sample.jobType(),
            sample.sourceId(),
            e.getMessage(),
            e
        );
        meterRegistry.counter("llm.usage.record.failure").increment();
    }

    /**
     * Match the source_id unique index by name so a genuine integrity failure (FK to a purged
     * workspace, a future NOT NULL column, numeric overflow) is never mistaken for a benign
     * re-bill and silently dropped.
     */
    private static boolean isSourceAlreadyBilled(DataIntegrityViolationException ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                return name != null && name.equalsIgnoreCase(SOURCE_UNIQUE_CONSTRAINT);
            }
            cur = cur.getCause();
        }
        return false;
    }

    /** Fires exactly on the event that crosses the cap (modulo concurrent-writer races — acceptable). */
    private void alertIfBudgetCrossed(Long workspaceId, @Nullable BigDecimal budget, @Nullable BigDecimal eventCost) {
        if (budget == null || eventCost == null || eventCost.signum() <= 0) {
            return;
        }
        BigDecimal monthToDate = budgetService.monthToDateCost(workspaceId);
        BigDecimal before = monthToDate.subtract(eventCost);
        if (monthToDate.compareTo(budget) >= 0 && before.compareTo(budget) < 0) {
            log.warn(
                "Workspace LLM budget exhausted — detection and mentor turns paused until the month rolls over " +
                    "or an instance admin raises the cap: workspaceId={}, budgetUsd={}, monthToDateUsd={}",
                workspaceId,
                budget,
                monthToDate
            );
            meterRegistry.counter("llm.budget.exhausted").increment();
        }
    }
}
