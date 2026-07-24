package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sole append path for the unified LLM usage ledger.
 *
 * <p>The caller supplies the price frozen at admission; completion never consults a mutable catalog and
 * never trusts a provider-reported model name for pricing. The insert participates in the caller's result
 * transaction, so accounting failure rolls that result back. Only an exact duplicate source attempt is
 * ignored by PostgreSQL's {@code ON CONFLICT DO NOTHING}.
 */
@Service
public class LlmUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(LlmUsageRecorder.class);

    private final LlmUsageEventRepository usageRepository;
    private final WorkspaceRepository workspaceRepository;
    private final LlmBudgetService budgetService;
    private final MeterRegistry meterRegistry;

    public LlmUsageRecorder(
        LlmUsageEventRepository usageRepository,
        WorkspaceRepository workspaceRepository,
        LlmBudgetService budgetService,
        MeterRegistry meterRegistry
    ) {
        this.usageRepository = usageRepository;
        this.workspaceRepository = workspaceRepository;
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
        LlmPriceSnapshot admitted = sample.price();
        LlmPriceSnapshot unverifiable = new LlmPriceSnapshot(
            admitted.fundingSource(),
            PricingState.UNPRICED,
            admitted.appliedPriceId(),
            admitted.appliedWorkspaceModelId(),
            admitted.per1mInputUsd(),
            admitted.per1mOutputUsd(),
            admitted.per1mCacheReadUsd(),
            admitted.per1mCacheWriteUsd()
        );
        if (persist(workspaceId, sample, unverifiable)) {
            meterRegistry.counter("llm.usage.unverifiable").increment();
        }
    }

    private boolean persist(Long workspaceId, LlmUsageSample sample, LlmPriceSnapshot price) {
        if (
            price.pricingState() == PricingState.PRICED &&
            ((sample.inputTokens() > 0 && price.per1mInputUsd() == null) ||
                (sample.outputTokens() > 0 && price.per1mOutputUsd() == null) ||
                (sample.cacheReadTokens() > 0 && price.per1mCacheReadUsd() == null) ||
                (sample.cacheWriteTokens() > 0 && price.per1mCacheWriteUsd() == null))
        ) {
            price = new LlmPriceSnapshot(
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
        BigDecimal cost = price.calculateCost(
            sample.inputTokens(),
            sample.outputTokens(),
            sample.cacheReadTokens(),
            sample.cacheWriteTokens()
        );
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
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        alertIfBudgetCrossed(workspaceId, cost);
                    } catch (RuntimeException e) {
                        log.warn("Post-commit LLM budget alert failed for workspaceId={}", workspaceId, e);
                        meterRegistry.counter("llm.budget.alert.failure").increment();
                    }
                }
            }
        );
    }

    private void alertIfBudgetCrossed(Long workspaceId, BigDecimal eventCost) {
        workspaceRepository
            .findById(workspaceId)
            .map(workspace -> workspace.getMonthlyLlmBudgetUsd())
            .ifPresent(budget -> {
                BigDecimal monthToDate = budgetService.monthToDateCost(workspaceId);
                if (monthToDate.compareTo(budget) >= 0 && monthToDate.subtract(eventCost).compareTo(budget) < 0) {
                    log.warn(
                        "Workspace LLM budget exhausted: workspaceId={}, budgetUsd={}, monthToDateUsd={}",
                        workspaceId,
                        budget,
                        monthToDate
                    );
                    meterRegistry.counter("llm.budget.exhausted").increment();
                }
            });
    }
}
