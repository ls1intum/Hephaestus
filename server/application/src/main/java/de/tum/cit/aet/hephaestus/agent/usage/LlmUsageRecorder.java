package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
import de.tum.cit.aet.hephaestus.core.TransactionCallbacks;
import de.tum.cit.aet.hephaestus.integration.core.spi.UsagePricingState;
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
            LlmUsageEventRepository usageRepository, LlmBudgetService budgetService, MeterRegistry meterRegistry) {
        this.usageRepository = usageRepository;
        this.budgetService = budgetService;
        this.meterRegistry = meterRegistry;
    }

    /** @param searchUnits what Cohere reranking calls billed instead of tokens; 0 for every other sample */
    public record LlmUsageSample(
            @Nullable UUID reviewId,
            String jobType,
            UsageSourceType sourceType,
            UUID sourceId,
            int sourceAttempt,
            @Nullable String model,
            long inputTokens,
            long outputTokens,
            long cacheReadTokens,
            long cacheWriteTokens,
            long reasoningTokens,
            long searchUnits,
            int totalCalls,
            LlmPriceSnapshot price,
            UsageProvenance provenance,
            MeasurementVerification measurementVerification,
            Instant occurredAt) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long workspaceId, LlmUsageSample sample) {
        if (sample.measurementVerification() == MeasurementVerification.UNKNOWN) {
            throw new IllegalArgumentException("New LLM usage requires a measurement verification decision");
        }
        boolean unverifiable = sample.measurementVerification() == MeasurementVerification.UNVERIFIABLE;
        if (persist(workspaceId, sample, unverifiable ? asUnpriced(sample.price()) : sample.price()) && unverifiable) {
            meterRegistry.counter(AgentMetrics.LLM_USAGE_UNVERIFIABLE).increment();
        }
    }

    // Missing rates leave the whole event unpriced; search units require their own rate.
    private static boolean isPricedWithARateMissing(LlmUsageSample sample, LlmPriceSnapshot price) {
        return (price.pricingState() == UsagePricingState.PRICED
                && ((sample.inputTokens() > 0 && price.per1mInputUsd() == null)
                        || (sample.outputTokens() > 0 && price.per1mOutputUsd() == null)
                        || (sample.cacheReadTokens() > 0 && price.per1mCacheReadUsd() == null)
                        || (sample.cacheWriteTokens() > 0 && price.per1mCacheWriteUsd() == null)
                        || sample.searchUnits() > 0));
    }

    /** Keeps the frozen rates: they still say what the attempt would have cost. */
    private static LlmPriceSnapshot asUnpriced(LlmPriceSnapshot price) {
        return new LlmPriceSnapshot(
                price.fundingSource(),
                UsagePricingState.UNPRICED,
                price.appliedPriceId(),
                price.appliedWorkspaceModelId(),
                price.per1mInputUsd(),
                price.per1mOutputUsd(),
                price.per1mCacheReadUsd(),
                price.per1mCacheWriteUsd());
    }

    private boolean persist(Long workspaceId, LlmUsageSample sample, LlmPriceSnapshot quoted) {
        int sourceAttempt = Math.max(0, sample.sourceAttempt());
        long unreportedCalls = sample.sourceType() == UsageSourceType.MENTOR_TURN
                ? 0
                : usageRepository.sumUnreportedPracticeScriptCalls(
                        workspaceId,
                        sample.sourceId(),
                        sourceAttempt,
                        sample.sourceType().name());
        // A PRICED snapshot missing a rate would silently cost the tokens it does have a rate for.
        LlmPriceSnapshot price = isPricedWithARateMissing(sample, quoted) ? asUnpriced(quoted) : quoted;
        LlmPriceSnapshot.Cost computed = price.calculateCost(
                sample.inputTokens(), sample.outputTokens(), sample.cacheReadTokens(), sample.cacheWriteTokens());
        BigDecimal cost = computed.usd();
        reportClamp(sample, computed);
        int inserted = usageRepository.insertIfAbsent(new LlmUsageInsert(
                UUID.randomUUID(),
                workspaceId,
                sample.reviewId(),
                sample.jobType(),
                sample.sourceType().name(),
                sample.sourceId(),
                sourceAttempt,
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
                price.per1mCacheWriteUsd(),
                sample.provenance().name(),
                Math.max(0, sample.searchUnits()),
                Math.toIntExact(unreportedCalls),
                sample.measurementVerification().name()));
        if (inserted == 0) {
            log.debug(
                    "LLM usage already recorded for sourceType={}, sourceId={}, sourceAttempt={}",
                    sample.sourceType(),
                    sample.sourceId(),
                    sample.sourceAttempt());
            return false;
        }
        if (price.pricingState() == UsagePricingState.UNPRICED) {
            meterRegistry.counter(AgentMetrics.LLM_USAGE_UNCOSTED).increment();
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
                "LLM cost clamped to fit the ledger column: clamp={}, storedUsd={}, sourceType={}, sourceId={},"
                        + " sourceAttempt={}",
                computed.clamp(),
                computed.usd(),
                sample.sourceType(),
                sample.sourceId(),
                sample.sourceAttempt());
        meterRegistry
                .counter(AgentMetrics.LLM_USAGE_COST_CLAMPED, "direction", clampTag(computed.clamp()))
                .increment();
    }

    private static String clampTag(LlmPriceSnapshot.CostClamp clamp) {
        return clamp == LlmPriceSnapshot.CostClamp.ROUNDED_UP_TO_MINIMUM ? "up_to_minimum" : "down_to_maximum";
    }

    private void registerBudgetAlert(Long workspaceId, LlmPriceSnapshot price, @Nullable BigDecimal cost) {
        if (price.fundingSource() != UsageFundingSource.INSTANCE
                || price.pricingState() != UsagePricingState.PRICED
                || cost == null
                || cost.signum() <= 0) {
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
                meterRegistry.counter(AgentMetrics.LLM_BUDGET_ALERT_FAILURE).increment();
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
        boolean crossedNow = LlmBudgetService.capReached(monthToDate, budget)
                && !LlmBudgetService.capReached(monthToDate.subtract(eventCost), budget);
        if (crossedNow) {
            log.warn(
                    "Workspace LLM budget exhausted: workspaceId={}, budgetUsd={}, monthToDateUsd={}",
                    workspaceId,
                    budget,
                    monthToDate);
            meterRegistry.counter(AgentMetrics.LLM_BUDGET_EXHAUSTED).increment();
        }
    }
}
