package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.job.PracticeScriptCallUsage;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobLlmUsageDelta;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobRepository;
import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
import de.tum.cit.aet.hephaestus.agent.proxy.ProxyRouting.BilledAttempt;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnWorkerRole;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.IntSupplier;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds one proxied call's token usage to the owning {@code review_job} row, or one practice script call to its
 * {@code review_job_script_usage} row, so a job that crashes mid-run still has the calls it made on
 * record and can be billed for them instead of recording zero.
 * Runs in its own {@code REQUIRES_NEW} transaction — the proxy servlet thread has no ambient
 * transaction, and the accounting write must commit independently of the passthrough.
 *
 * <p>A failed write is money lost, not a no-op: these running totals are what a run without a
 * {@code usage.json} is billed on, so a non-zero rate on {@code llm.proxy.usage.accumulate.failure}
 * means the ledger is under-billing.
 */
@Service
@ConditionalOnWorkerRole
public class ProxyUsageAccumulator {

    private static final Logger log = LoggerFactory.getLogger(ProxyUsageAccumulator.class);

    private final ReviewJobRepository agentJobRepository;
    private final MeterRegistry meterRegistry;

    ProxyUsageAccumulator(ReviewJobRepository agentJobRepository, MeterRegistry meterRegistry) {
        this.agentJobRepository = agentJobRepository;
        this.meterRegistry = meterRegistry;
    }

    /** Never throws, so accounting can never break the proxied response. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void accumulate(@Nullable BilledAttempt attempt, @Nullable ProxyTokenUsage usage) {
        if (attempt == null || usage == null) {
            return;
        }
        fenced(
                attempt,
                () -> agentJobRepository.accumulateLlmUsage(
                        attempt.sourceId(),
                        attempt.number(),
                        new ReviewJobLlmUsageDelta(
                                usage.billableInputTokens(),
                                usage.outputTokens(),
                                usage.reasoningTokens(),
                                usage.cacheReadTokens(),
                                usage.cacheWriteTokens())));
    }

    /**
     * Adds one proxied practice script call to the {@code review_job_script_usage} row of its kind and
     * practice. Never throws.
     *
     * @param usage {@code null} when the provider reported no tokens; the call still counts
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void accumulatePracticeScript(
            BilledAttempt attempt, PracticeScriptCall call, @Nullable ProxyTokenUsage usage) {
        PracticeScriptCallUsage counted = usage == null
                ? new PracticeScriptCallUsage(call.kind(), call.practiceSlug(), call.tier(), 0, 0, 0, 0, 0, false)
                : new PracticeScriptCallUsage(
                        call.kind(),
                        call.practiceSlug(),
                        call.tier(),
                        usage.billableInputTokens(),
                        usage.outputTokens(),
                        usage.cacheReadTokens(),
                        usage.cacheWriteTokens(),
                        usage.searchUnits());
        fenced(
                attempt,
                () -> agentJobRepository.accumulatePracticeScriptUsage(attempt.sourceId(), attempt.number(), counted));
    }

    /** @param write returns the rows the attempt fence let through */
    private void fenced(BilledAttempt attempt, IntSupplier write) {
        try {
            if (write.getAsInt() == 0) {
                recordSuperseded(attempt);
            }
        } catch (RuntimeException e) {
            log.warn("Lost proxy usage accounting for job {} — this call may go unbilled", attempt.sourceId(), e);
            meterRegistry
                    .counter(AgentMetrics.LLM_PROXY_USAGE_ACCUMULATE_FAILURE)
                    .increment();
        }
    }

    /**
     * The attempt fence rejected this write: the row has moved on since the token was authenticated.
     * Dropping under-bills by one call; adding would charge one attempt's tokens to another, at
     * another price and possibly from the other purse.
     */
    private void recordSuperseded(BilledAttempt attempt) {
        log.warn(
                "Dropping proxy usage from a superseded attempt — job {} is no longer running attempt {}; "
                        + "this call goes unbilled rather than being charged to whoever owns the row now",
                attempt.sourceId(),
                attempt.number());
        meterRegistry
                .counter(AgentMetrics.LLM_PROXY_USAGE_ACCUMULATE_SUPERSEDED)
                .increment();
    }
}
