package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPrice;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelPriceRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.pricing.ModelPricingService;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * <h2>Cost is derived server-side only (#1368 slice 6)</h2>
 *
 * <p>The runner never reports a cost; this is the single choke point where a cost is resolved, so
 * every writer gets the same number for the same catalog binding. Resolution order for one event:
 *
 * <ol>
 *   <li>The caller's frozen catalog binding ({@link LlmUsageSample#connectionScope} +
 *       {@link LlmUsageSample#connectionId}, carried from {@code ConfigSnapshot} /
 *       {@code MentorLlmConfig}) resolves to the instance {@link LlmModel}'s current
 *       {@link LlmModelPrice} row, or the workspace's own {@link WorkspaceLlmModel} and its inline
 *       price fields.</li>
 *   <li>No catalog binding at all (a legacy, pre-#1368 config) falls back to
 *       {@link ModelPricingService}'s global registry, exactly as before.</li>
 *   <li>Still unknown = the ledger stores {@code cost_usd = NULL} and {@code pricing_state =
 *       UNPRICED} — never a silent $0. For a <em>capped, instance-funded</em> workspace this WARNs
 *       ({@code llm.usage.uncosted}): tokens burned that the cap cannot see are exactly the blind
 *       spot this feature exists to remove. A model explicitly declared FREE prices at exactly $0
 *       and satisfies the cap like any other confirmed spend.</li>
 * </ol>
 *
 * <p>Budget-crossing alert: if the workspace has a monthly cap and this event's BUDGETED
 * contribution (priced + instance-funded) pushed month-to-date spend across it, emit a WARN
 * (Sentry-visible) plus the {@code llm.budget.exhausted} counter — the single "you are now
 * paused" operator signal. A workspace's own (BYO) spend never counts toward its cap and so never
 * fires this alert.
 */
@Service
public class LlmUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageRecorder.class);

    /** Name of the ledger's source_id unique index — the one integrity failure that is benign. */
    private static final String SOURCE_UNIQUE_CONSTRAINT = "ux_llm_usage_event_source";

    private static final BigDecimal PER_1M = BigDecimal.valueOf(1_000_000L);

    /** {@code NUMERIC(18,6)} column scale — 6 digits after the decimal point. */
    private static final int COST_SCALE = 6;

    /** Smallest amount the column can represent; used instead of silently zeroing a real, tiny cost. */
    private static final BigDecimal MIN_REPRESENTABLE_COST = new BigDecimal("0.000001");

    /**
     * Largest amount {@code NUMERIC(18,6)} can hold (12 integer digits + 6 fractional digits; widened
     * from {@code NUMERIC(12,6)} — #1368 migration-correctness fix, see changelog 1784566728230-17). A
     * single ledger event costing ≥ $1 trillion is unreachable in practice; this is a last-ditch guard
     * against a corrupted rate row, not a bound anyone should ever hit.
     */
    private static final BigDecimal MAX_REPRESENTABLE_COST = new BigDecimal("999999999999.999999");

    /** {@code NUMERIC(18,6)} overflows at 10^12 — a computed cost at or above this is clamped. */
    private static final BigDecimal OVERFLOW_THRESHOLD = BigDecimal.valueOf(1_000_000_000_000L);

    private static final BigDecimal ZERO_COST = BigDecimal.ZERO.setScale(COST_SCALE, RoundingMode.UNNECESSARY);

    private final LlmUsageEventRepository usageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final LlmBudgetService budgetService;
    private final ModelPricingService pricingService;
    private final LlmModelRepository llmModelRepository;
    private final LlmModelPriceRepository llmModelPriceRepository;
    private final WorkspaceLlmModelRepository workspaceLlmModelRepository;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate requiresNewTx;

    public LlmUsageRecorder(
        LlmUsageEventRepository usageRepository,
        WorkspaceRepository workspaceRepository,
        LlmBudgetService budgetService,
        ModelPricingService pricingService,
        LlmModelRepository llmModelRepository,
        LlmModelPriceRepository llmModelPriceRepository,
        WorkspaceLlmModelRepository workspaceLlmModelRepository,
        MeterRegistry meterRegistry,
        PlatformTransactionManager transactionManager
    ) {
        this.usageRepository = usageRepository;
        this.workspaceRepository = workspaceRepository;
        this.budgetService = budgetService;
        this.pricingService = pricingService;
        this.llmModelRepository = llmModelRepository;
        this.llmModelPriceRepository = llmModelPriceRepository;
        this.workspaceLlmModelRepository = workspaceLlmModelRepository;
        this.meterRegistry = meterRegistry;
        this.requiresNewTx = new TransactionTemplate(transactionManager);
        this.requiresNewTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * One unit of LLM work to append to the ledger. Token counts clamp negative/null to zero at
     * the call site. {@code connectionScope}/{@code connectionId} identify the frozen catalog
     * binding that funded the call (mirrors {@code LlmModelResolver.ConnectionRef}) — both null
     * means a legacy, pre-#1368 config with no catalog binding.
     */
    public record LlmUsageSample(
        LlmUsageJobType jobType,
        UUID sourceId,
        @Nullable String model,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long reasoningTokens,
        int totalCalls,
        @Nullable FundingSource connectionScope,
        @Nullable Long connectionId,
        Instant occurredAt
    ) {}

    /** Resolved pricing outcome for one event — everything {@link #record} needs to persist it. */
    private record PriceResolution(
        PricingState pricingState,
        @Nullable BigDecimal costUsd,
        @Nullable Long appliedPriceId,
        @Nullable Long appliedWorkspaceModelId,
        AppliedRates appliedRates,
        FundingSource fundingSource
    ) {}

    /**
     * Frozen per-1M-token rates actually applied to a PRICED event (#1368 slice 6) — see
     * {@link LlmUsageEvent}'s field doc for why these are stored rather than trusting the live
     * catalog row. {@link #NONE} for FREE (rate is moot) and UNPRICED (no rate was ever resolved).
     */
    private record AppliedRates(
        @Nullable BigDecimal perInputUsd,
        @Nullable BigDecimal perOutputUsd,
        @Nullable BigDecimal perCacheReadUsd,
        @Nullable BigDecimal perCacheWriteUsd,
        @Nullable BigDecimal perReasoningUsd
    ) {
        static final AppliedRates NONE = new AppliedRates(null, null, null, null, null);
    }

    /**
     * Append one ledger row. Never throws — see class doc. MUST be called AFTER the caller's own
     * transaction commits: the recorder's {@code REQUIRES_NEW} would otherwise hold a second pool
     * connection under the caller's, and a caller that rolls back would leave a billed row behind.
     */
    public void record(Long workspaceId, LlmUsageSample sample) {
        try {
            requiresNewTx.executeWithoutResult(tx -> {
                Workspace workspace = workspaceRepository.getReferenceById(workspaceId);
                PriceResolution resolution = resolvePricing(workspaceId, workspace.getMonthlyLlmBudgetUsd(), sample);
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
                event.setReasoningTokens(Math.max(0, sample.reasoningTokens()));
                event.setTotalCalls(Math.max(1, sample.totalCalls()));
                event.setCostUsd(resolution.costUsd());
                event.setPricingState(resolution.pricingState());
                event.setFundingSource(resolution.fundingSource());
                event.setAppliedPriceId(resolution.appliedPriceId());
                event.setAppliedWorkspaceModelId(resolution.appliedWorkspaceModelId());
                event.setAppliedPer1mInputUsd(resolution.appliedRates().perInputUsd());
                event.setAppliedPer1mOutputUsd(resolution.appliedRates().perOutputUsd());
                event.setAppliedPer1mCacheReadUsd(resolution.appliedRates().perCacheReadUsd());
                event.setAppliedPer1mCacheWriteUsd(resolution.appliedRates().perCacheWriteUsd());
                event.setAppliedPer1mReasoningUsd(resolution.appliedRates().perReasoningUsd());
                event.setOccurredAt(sample.occurredAt());
                usageRepository.saveAndFlush(event);
                alertIfBudgetCrossed(workspaceId, workspace.getMonthlyLlmBudgetUsd(), resolution);
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
     * Resolve the price for one event, per the class doc's precedence: frozen catalog binding,
     * then legacy pricing table (no-binding only), then unknown.
     */
    private PriceResolution resolvePricing(Long workspaceId, @Nullable BigDecimal budget, LlmUsageSample sample) {
        FundingSource scope = sample.connectionScope();
        Long connectionId = sample.connectionId();
        PriceResolution resolution;
        if (scope == FundingSource.INSTANCE && connectionId != null) {
            resolution = resolveInstanceCatalog(connectionId, sample);
        } else if (scope == FundingSource.WORKSPACE && connectionId != null) {
            resolution = resolveWorkspaceCatalog(connectionId, sample);
        } else {
            resolution = resolveLegacyFallback(sample);
        }
        if (resolution.pricingState() == PricingState.UNPRICED) {
            warnUncosted(workspaceId, budget, sample, resolution.fundingSource());
        }
        return resolution;
    }

    /** Price from the instance catalog: the model bound to this call's frozen connection, and its open price row. */
    private PriceResolution resolveInstanceCatalog(Long connectionId, LlmUsageSample sample) {
        LlmModel model = firstInstanceMatch(findInstanceModel(connectionId, sample.model()));
        if (model == null) {
            return new PriceResolution(
                PricingState.UNPRICED,
                null,
                null,
                null,
                AppliedRates.NONE,
                FundingSource.INSTANCE
            );
        }
        Optional<LlmModelPrice> priceOpt = llmModelPriceRepository.findByModelIdAndEffectiveToIsNull(model.getId());
        if (priceOpt.isEmpty()) {
            return new PriceResolution(
                PricingState.UNPRICED,
                null,
                null,
                null,
                AppliedRates.NONE,
                FundingSource.INSTANCE
            );
        }
        LlmModelPrice price = priceOpt.get();
        return switch (price.getPricingMode()) {
            case FREE -> new PriceResolution(
                PricingState.FREE,
                ZERO_COST,
                price.getId(),
                null,
                AppliedRates.NONE,
                FundingSource.INSTANCE
            );
            case UNPRICED -> new PriceResolution(
                PricingState.UNPRICED,
                null,
                price.getId(),
                null,
                AppliedRates.NONE,
                FundingSource.INSTANCE
            );
            case PRICED -> {
                // Freeze the rates onto the event, not just the price row's id (#1368 slice 6):
                // applied_price_id alone dies the moment that row is superseded — a reader would see
                // a resolvable id but its CURRENT rates, which no longer describe this HISTORICAL charge.
                AppliedRates rates = new AppliedRates(
                    price.getPer1mInputUsd(),
                    price.getPer1mOutputUsd(),
                    price.getPer1mCacheReadUsd(),
                    price.getPer1mCacheWriteUsd(),
                    price.getPer1mReasoningUsd()
                );
                BigDecimal raw = computeRawCost(
                    sample,
                    rates.perInputUsd(),
                    rates.perOutputUsd(),
                    rates.perCacheReadUsd(),
                    rates.perCacheWriteUsd(),
                    rates.perReasoningUsd()
                );
                yield new PriceResolution(
                    PricingState.PRICED,
                    applyNumericGuard(raw, sample),
                    price.getId(),
                    null,
                    rates,
                    FundingSource.INSTANCE
                );
            }
        };
    }

    /**
     * Price from a workspace's own (BYO) catalog: the model bound to this call's frozen
     * connection, using its inline price fields directly (no temporal price history on this side —
     * see {@link WorkspaceLlmModel}'s class doc). No {@code applied_price_id}: that column's
     * documented meaning is strictly an instance {@code llm_model_price} row (soft ref into a
     * different table); {@code applied_workspace_model_id} is the BYO-side equivalent instead.
     */
    private PriceResolution resolveWorkspaceCatalog(Long connectionId, LlmUsageSample sample) {
        WorkspaceLlmModel model = firstWorkspaceMatch(findWorkspaceModel(connectionId, sample.model()));
        if (model == null) {
            return new PriceResolution(
                PricingState.UNPRICED,
                null,
                null,
                null,
                AppliedRates.NONE,
                FundingSource.WORKSPACE
            );
        }
        return switch (model.getPricingMode()) {
            case FREE -> new PriceResolution(
                PricingState.FREE,
                ZERO_COST,
                null,
                model.getId(),
                AppliedRates.NONE,
                FundingSource.WORKSPACE
            );
            case UNPRICED -> new PriceResolution(
                PricingState.UNPRICED,
                null,
                null,
                model.getId(),
                AppliedRates.NONE,
                FundingSource.WORKSPACE
            );
            case PRICED -> {
                // Freeze the rates AND the model id (#1368 slice 6): workspace_llm_model has no price
                // history — the inline rate can change under a live row — so both are needed to make
                // this event falsifiable later.
                AppliedRates rates = new AppliedRates(
                    model.getPer1mInputUsd(),
                    model.getPer1mOutputUsd(),
                    model.getPer1mCacheReadUsd(),
                    model.getPer1mCacheWriteUsd(),
                    model.getPer1mReasoningUsd()
                );
                BigDecimal raw = computeRawCost(
                    sample,
                    rates.perInputUsd(),
                    rates.perOutputUsd(),
                    rates.perCacheReadUsd(),
                    rates.perCacheWriteUsd(),
                    rates.perReasoningUsd()
                );
                yield new PriceResolution(
                    PricingState.PRICED,
                    applyNumericGuard(raw, sample),
                    null,
                    model.getId(),
                    rates,
                    FundingSource.WORKSPACE
                );
            }
        };
    }

    /**
     * Legacy path: a config with no catalog binding at all (pre-#1368). Falls back to the global
     * {@link ModelPricingService} registry exactly as before slice 6 — always instance-funded, since
     * BYO connections didn't exist prior to the catalog. No rate snapshot: {@link ModelPricingService}
     * prices in a different unit (per-1K, not per-1M) and is itself already effective-dated
     * (validFrom/validTo), and this path is deprecate-then-remove — an operator rebinds a config onto a
     * catalog model at their own pace (no automatic backfill, see the changelog's -11 removal note),
     * at which point it gets full provenance above.
     */
    private PriceResolution resolveLegacyFallback(LlmUsageSample sample) {
        if (sample.model() == null || sample.model().isBlank()) {
            return new PriceResolution(
                PricingState.UNPRICED,
                null,
                null,
                null,
                AppliedRates.NONE,
                FundingSource.INSTANCE
            );
        }
        Optional<BigDecimal> derived = pricingService.computeCost(
            sample.model(),
            Math.max(0, sample.inputTokens()),
            Math.max(0, sample.outputTokens()),
            Math.max(0, sample.cacheReadTokens()),
            Math.max(0, sample.cacheWriteTokens())
        );
        if (derived.isEmpty()) {
            return new PriceResolution(
                PricingState.UNPRICED,
                null,
                null,
                null,
                AppliedRates.NONE,
                FundingSource.INSTANCE
            );
        }
        return new PriceResolution(
            PricingState.PRICED,
            applyNumericGuard(derived.get(), sample),
            null,
            null,
            AppliedRates.NONE,
            FundingSource.INSTANCE
        );
    }

    private List<LlmModel> findInstanceModel(Long connectionId, @Nullable String upstreamModelId) {
        if (upstreamModelId == null || upstreamModelId.isBlank()) {
            return List.of();
        }
        return llmModelRepository.findByConnectionIdAndUpstreamModelId(connectionId, upstreamModelId);
    }

    private List<WorkspaceLlmModel> findWorkspaceModel(Long connectionId, @Nullable String upstreamModelId) {
        if (upstreamModelId == null || upstreamModelId.isBlank()) {
            return List.of();
        }
        return workspaceLlmModelRepository.findByConnectionIdAndUpstreamModelId(connectionId, upstreamModelId);
    }

    @Nullable
    private static LlmModel firstInstanceMatch(List<LlmModel> models) {
        return models.stream().filter(LlmModel::isEnabled).findFirst().orElse(models.isEmpty() ? null : models.get(0));
    }

    @Nullable
    private static WorkspaceLlmModel firstWorkspaceMatch(List<WorkspaceLlmModel> models) {
        return models
            .stream()
            .filter(WorkspaceLlmModel::isEnabled)
            .findFirst()
            .orElse(models.isEmpty() ? null : models.get(0));
    }

    /**
     * Sum {@code tokens/1e6 * rate} across every bucket where BOTH the token count and the rate are
     * present (input, output, cache-read, cache-write, reasoning). Unrounded — see
     * {@link #applyNumericGuard} for the storage-scale rounding + overflow guard.
     */
    private static BigDecimal computeRawCost(
        LlmUsageSample sample,
        @Nullable BigDecimal perInputUsd,
        @Nullable BigDecimal perOutputUsd,
        @Nullable BigDecimal perCacheReadUsd,
        @Nullable BigDecimal perCacheWriteUsd,
        @Nullable BigDecimal perReasoningUsd
    ) {
        MathContext mc = new MathContext(20, RoundingMode.HALF_EVEN);
        BigDecimal total = BigDecimal.ZERO;
        total = total.add(bucket(sample.inputTokens(), perInputUsd, mc), mc);
        total = total.add(bucket(sample.outputTokens(), perOutputUsd, mc), mc);
        total = total.add(bucket(sample.cacheReadTokens(), perCacheReadUsd, mc), mc);
        total = total.add(bucket(sample.cacheWriteTokens(), perCacheWriteUsd, mc), mc);
        total = total.add(bucket(sample.reasoningTokens(), perReasoningUsd, mc), mc);
        return total;
    }

    private static BigDecimal bucket(long tokens, @Nullable BigDecimal perMillionRate, MathContext mc) {
        if (perMillionRate == null || tokens <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens).divide(PER_1M, mc).multiply(perMillionRate, mc);
    }

    /**
     * {@code NUMERIC(18,6)} storage guard (#1368 slice 6; widened from {@code NUMERIC(12,6)} — see
     * changelog 1784566728230-17): rounds HALF_EVEN to the column scale, then defends the two edges a
     * raw computed cost could otherwise hit.
     *
     * <ul>
     *   <li>A genuinely non-zero cost that rounds to exactly {@code 0.000000} (a very small per-token
     *       rate over very few tokens) would silently look identical to a declared-free model. Store
     *       the smallest representable amount instead so the spend is visible, not erased.</li>
     *   <li>A cost at or above {@code 10^12} overflows the column's 12 integer digits. This is not a
     *       real-world number ($1 trillion for one ledger event) — hitting it means a corrupted price
     *       row or a unit-conversion bug, not a legitimately large bill, so it logs at ERROR. Still
     *       clamp to the max representable value rather than let the INSERT fail and drop the whole
     *       ledger row.</li>
     * </ul>
     */
    private static BigDecimal applyNumericGuard(BigDecimal raw, LlmUsageSample sample) {
        if (raw.signum() < 0) {
            // Rates and token counts are both non-negative by construction; a negative result would
            // mean a data-entry error in a price row. Floor at zero rather than propagate a credit.
            log.warn("Computed a negative LLM cost (raw={}) — flooring to zero: sourceId={}", raw, sample.sourceId());
            return ZERO_COST;
        }
        BigDecimal rounded = raw.setScale(COST_SCALE, RoundingMode.HALF_EVEN);
        if (raw.signum() > 0 && rounded.signum() == 0) {
            log.debug(
                "Computed LLM cost rounded to zero (raw={}) — storing the minimum representable amount instead: " +
                    "sourceId={}",
                raw,
                sample.sourceId()
            );
            return MIN_REPRESENTABLE_COST;
        }
        if (rounded.compareTo(OVERFLOW_THRESHOLD) >= 0) {
            log.error(
                "Computed LLM cost exceeds the ledger's NUMERIC(18,6) capacity (raw={}) — this should be " +
                    "unreachable in practice and likely indicates a corrupted price row; clamping: sourceId={}",
                raw,
                sample.sourceId()
            );
            return MAX_REPRESENTABLE_COST;
        }
        return rounded;
    }

    /**
     * Tokens burned with no resolvable cost contribute zero to the budgeted month SUM, so a capped
     * workspace could in principle spend past its cap unpaused. Loud for a capped, INSTANCE-funded
     * event (the cap is the thing being undermined); DEBUG otherwise — a workspace's own (BYO) spend
     * never counts toward the cap, so an unpriced BYO event doesn't threaten it. The report surfaces
     * the same count as {@code unpricedEventCount} so an admin/workspace-admin sees the gap without
     * reading logs.
     */
    private void warnUncosted(
        Long workspaceId,
        @Nullable BigDecimal budget,
        LlmUsageSample sample,
        FundingSource fundingSource
    ) {
        meterRegistry.counter("llm.usage.uncosted").increment();
        if (budget == null || fundingSource != FundingSource.INSTANCE) {
            log.debug(
                "LLM usage with no resolvable cost: workspaceId={}, model={}, fundingSource={}",
                workspaceId,
                sample.model(),
                fundingSource
            );
            return;
        }
        log.warn(
            "LLM usage with no resolvable cost for a BUDGETED workspace — this usage is invisible to the monthly " +
                "cap (verdict becomes UNVERIFIABLE, never fail-closed in v1). Add a price for the model: " +
                "workspaceId={}, model={}, jobType={}, inputTokens={}, outputTokens={}",
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

    /**
     * Fires exactly on the event that crosses the cap (modulo concurrent-writer races —
     * acceptable). Only a PRICED, INSTANCE-funded event can cross the cap: BYO spend and unpriced
     * events never contribute to the budgeted sum, so they never trigger this alert.
     */
    private void alertIfBudgetCrossed(Long workspaceId, @Nullable BigDecimal budget, PriceResolution resolution) {
        if (
            budget == null ||
            resolution.pricingState() != PricingState.PRICED ||
            resolution.fundingSource() != FundingSource.INSTANCE ||
            resolution.costUsd() == null ||
            resolution.costUsd().signum() <= 0
        ) {
            return;
        }
        BigDecimal monthToDate = budgetService.monthToDateCost(workspaceId);
        BigDecimal before = monthToDate.subtract(resolution.costUsd());
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
