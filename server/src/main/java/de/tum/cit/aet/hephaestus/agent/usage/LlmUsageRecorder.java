package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.core.TransactionCallbacks;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sole append path for the LLM usage ledger. The caller supplies the price frozen at admission;
 * completion never consults a mutable catalog and never trusts a provider-reported model name for
 * pricing. The insert participates in the caller's result transaction, so accounting failure rolls that
 * result back.
 */
@Service
public class LlmUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageRecorder.class);

    private final LlmUsageEventRepository usageRepository;
    private final LlmBudgetService budgetService;
    private final MeterRegistry meterRegistry;

    public LlmUsageRecorder(
        LlmUsageEventRepository usageRepository,
        LlmBudgetService budgetService,
        MeterRegistry meterRegistry
    ) {
        this.usageRepository = usageRepository;
        this.budgetService = budgetService;
        this.meterRegistry = meterRegistry;
    }

    public record LlmUsageSample(
        LlmUsageJobType jobType,
        LlmUsageSourceType sourceType,
        UUID sourceId,
        int sourceAttempt,
        @Nullable String model,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long reasoningTokens,
        int totalCalls,
        LlmPriceSnapshot price,
        Instant occurredAt
    ) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long workspaceId, LlmUsageSample sample) {
        persist(workspaceId, sample, sample.price());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordUnverifiable(Long workspaceId, LlmUsageSample sample) {
        if (persist(workspaceId, sample, asUnpriced(sample.price()))) {
            meterRegistry.counter("llm.usage.unverifiable").increment();
        }
    }

    /** A rate the frozen price is missing cannot be charged, so the whole event stops being priced. */
    private static boolean isPricedWithARateMissing(LlmUsageSample sample, LlmPriceSnapshot price) {
        return (
            price.pricingState() == PricingState.PRICED &&
            ((sample.inputTokens() > 0 && price.per1mInputUsd() == null) ||
                (sample.outputTokens() > 0 && price.per1mOutputUsd() == null) ||
                (sample.cacheReadTokens() > 0 && price.per1mCacheReadUsd() == null) ||
                (sample.cacheWriteTokens() > 0 && price.per1mCacheWriteUsd() == null))
        );
    }

    /** Keeps the frozen rates: they still say what the attempt would have cost. */
    private static LlmPriceSnapshot asUnpriced(LlmPriceSnapshot price) {
        return new LlmPriceSnapshot(
            price.fundingSource(),
            PricingState.UNPRICED,
            price.appliedPriceId(),
            price.appliedWorkspaceModelId(),
            price.per1mInputUsd(),
            price.per1mOutputUsd(),
            price.per1mCacheReadUsd(),
            price.per1mCacheWriteUsd()
        );
    }

    private boolean persist(Long workspaceId, LlmUsageSample sample, LlmPriceSnapshot price) {
        if (isPricedWithARateMissing(sample, price)) {
            price = asUnpriced(price);
        }
        LlmPriceSnapshot.Cost computed = price.calculateCost(
            sample.inputTokens(),
            sample.outputTokens(),
            sample.cacheReadTokens(),
            sample.cacheWriteTokens()
        );
        BigDecimal cost = computed.usd();
        reportClamp(sample, computed);
        int inserted = usageRepository.insertIfAbsent(
            new LlmUsageInsert(
                UUID.randomUUID(),
                workspaceId,
                sample.jobType().name(),
                sample.sourceType().name(),
                sample.sourceId(),
                Math.max(0, sample.sourceAttempt()),
                sample.model(),
                Math.max(0, sample.inputTokens()),
                Math.max(0, sample.outputTokens()),
                Math.max(0, sample.cacheReadTokens()),
                Math.max(0, sample.cacheWriteTokens()),
                Math.max(0, sample.reasoningTokens()),
                Math.max(1, sample.totalCalls()),
                cost,
                sample.occurredAt(),
                price.pricingState().name(),
                price.fundingSource().name(),
                price.appliedPriceId(),
                price.appliedWorkspaceModelId(),
                price.per1mInputUsd(),
                price.per1mOutputUsd(),
                price.per1mCacheReadUsd(),
                price.per1mCacheWriteUsd()
            )
        );
        if (inserted == 0) {
            log.debug(
                "LLM usage already recorded for sourceType={}, sourceId={}, sourceAttempt={}",
                sample.sourceType(),
                sample.sourceId(),
                sample.sourceAttempt()
            );
            return false;
        }
        if (price.pricingState() == PricingState.UNPRICED) {
            meterRegistry.counter("llm.usage.uncosted").increment();
        }
        registerBudgetAlert(workspaceId, price, cost);
        return true;
    }

    /**
     * Emitted before the insert, so the signal survives a failed write — a clamp is a fact about the
     * price and the token counts, not about whether the row landed.
     */
    private void reportClamp(LlmUsageSample sample, LlmPriceSnapshot.Cost computed) {
        if (computed.clamp() == null) {
            return;
        }
        log.warn(
            "LLM cost clamped to fit the ledger column: clamp={}, storedUsd={}, sourceType={}, sourceId={}, sourceAttempt={}",
            computed.clamp(),
            computed.usd(),
            sample.sourceType(),
            sample.sourceId(),
            sample.sourceAttempt()
        );
        meterRegistry.counter("llm.usage.cost.clamped", "direction", clampTag(computed.clamp())).increment();
    }

    private static String clampTag(LlmPriceSnapshot.CostClamp clamp) {
        return clamp == LlmPriceSnapshot.CostClamp.ROUNDED_UP_TO_MINIMUM ? "up_to_minimum" : "down_to_maximum";
    }

    private void registerBudgetAlert(Long workspaceId, LlmPriceSnapshot price, @Nullable BigDecimal cost) {
        if (
            price.fundingSource() != FundingSource.INSTANCE ||
            price.pricingState() != PricingState.PRICED ||
            cost == null ||
            cost.signum() <= 0
        ) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("LLM usage must be recorded in the source result transaction");
        }
        TransactionCallbacks.afterCommit(() -> {
            try {
                alertIfBudgetCrossed(workspaceId, cost);
            } catch (RuntimeException e) {
                log.warn("Post-commit LLM budget alert failed for workspaceId={}", workspaceId, e);
                meterRegistry.counter("llm.budget.alert.failure").increment();
            }
        });
    }

    /**
     * WARNs on the crossing, not the state, so a month that stays exhausted does not log on every
     * subsequent event.
     */
    private void alertIfBudgetCrossed(Long workspaceId, BigDecimal eventCost) {
        LlmBudgetHeadroom headroom = budgetService.headroom(workspaceId);
        BigDecimal budget = headroom.instanceBudgetUsd();
        BigDecimal monthToDate = headroom.instanceSpentUsd();
        if (budget == null || monthToDate == null) {
            return;
        }
        boolean crossedNow =
            LlmBudgetService.capReached(monthToDate, budget) &&
            !LlmBudgetService.capReached(monthToDate.subtract(eventCost), budget);
        if (crossedNow) {
            log.warn(
                "Workspace LLM budget exhausted: workspaceId={}, budgetUsd={}, monthToDateUsd={}",
                workspaceId,
                budget,
                monthToDate
            );
            meterRegistry.counter("llm.budget.exhausted").increment();
        }
    }
}
