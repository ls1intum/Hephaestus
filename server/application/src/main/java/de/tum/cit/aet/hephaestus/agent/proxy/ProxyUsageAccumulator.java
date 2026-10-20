package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.job.PracticeScriptCallUsage;
import de.tum.cit.aet.hephaestus.agent.job.ReviewAttemptUsageService;
import de.tum.cit.aet.hephaestus.agent.job.ReviewAttemptUsageService.CallSettlement;
import de.tum.cit.aet.hephaestus.agent.job.ReviewJobLlmUsageDelta;
import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
import de.tum.cit.aet.hephaestus.agent.proxy.ProxyRouting.BilledAttempt;
import de.tum.cit.aet.hephaestus.agent.usage.UsageSourceType;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnWorkerRole;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Settles each returned review call on the attempt that authenticated it, in its own transaction, so a crashed job
 * retains measured spend and accounting can never break the proxied response.
 *
 * <p>{@link LlmProxyService} settles a forwarded call at most once: a non-streamed answer when its body arrives, a
 * stream once when it ends, and a practice script call that failed before an answer once without usage. A refused or
 * unserved call settles nothing. Each settlement is one transaction that adds the practice script row and the
 * attempt's proxy total together.
 */
@Service
@ConditionalOnWorkerRole
public class ProxyUsageAccumulator {

    private static final Logger log = LoggerFactory.getLogger(ProxyUsageAccumulator.class);

    private final ReviewAttemptUsageService attemptUsage;
    private final TransactionTemplate ownTransaction;
    private final MeterRegistry meterRegistry;

    ProxyUsageAccumulator(
            ReviewAttemptUsageService attemptUsage,
            PlatformTransactionManager transactionManager,
            MeterRegistry meterRegistry) {
        this.attemptUsage = attemptUsage;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.meterRegistry = meterRegistry;
    }

    /**
     * Never throws.
     *
     * @param call the practice script call; {@code null} for the review's own call
     * @param usage {@code null} when the provider reported no tokens. A practice script call still counts; the
     *     review's own call then has nothing to settle
     */
    public void accumulate(
            @Nullable BilledAttempt attempt, @Nullable PracticeScriptCall call, @Nullable ProxyTokenUsage usage) {
        if (attempt == null) return;
        Long workspaceId = attempt.workspaceId();
        PracticeScriptCallUsage script = call == null ? null : scriptUsage(call, usage);
        // Only calls of the review's own model add to its row; another kind is billed from its script row alone.
        ReviewJobLlmUsageDelta delta = usage == null || attempt.sourceType() != UsageSourceType.REVIEW_JOB
                ? null
                : new ReviewJobLlmUsageDelta(
                        usage.billableInputTokens(),
                        usage.outputTokens(),
                        usage.reasoningTokens(),
                        usage.cacheReadTokens(),
                        usage.cacheWriteTokens());
        if (script == null && delta == null) return;
        if (workspaceId == null) {
            recordDropped(attempt, "no workspace");
            return;
        }
        long workspace = workspaceId;
        try {
            CallSettlement settled = ownTransaction.execute(
                    status -> attemptUsage.settleCall(attempt.sourceId(), workspace, attempt.number(), script, delta));
            if (settled == CallSettlement.LATE) {
                meterRegistry.counter(AgentMetrics.LLM_PROXY_USAGE_LATE_SETTLED).increment();
            } else if (settled != CallSettlement.CURRENT) {
                recordDropped(attempt, settled == null ? "no settlement" : settled.name());
            }
        } catch (RuntimeException e) {
            log.warn("Lost proxy usage accounting for job {} — this call may go unbilled", attempt.sourceId(), e);
            meterRegistry
                    .counter(AgentMetrics.LLM_PROXY_USAGE_ACCUMULATE_FAILURE)
                    .increment();
        }
    }

    private static PracticeScriptCallUsage scriptUsage(PracticeScriptCall call, @Nullable ProxyTokenUsage usage) {
        return usage == null
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
    }

    /**
     * The call has no attempt to land on: the job was deleted, or its attempt was never registered. Dropping
     * under-bills by one call; creating an attempt for it would bring back what erasure removed.
     */
    private void recordDropped(BilledAttempt attempt, String reason) {
        log.warn(
                "Dropping proxy usage with no attempt to settle on — job {} attempt {} ({}); this call goes unbilled",
                attempt.sourceId(),
                attempt.number(),
                reason);
        meterRegistry
                .counter(AgentMetrics.LLM_PROXY_USAGE_ACCUMULATE_SUPERSEDED)
                .increment();
    }
}
