package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.proxy.ProxyRouting.BilledAttempt;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds one proxied call's token usage to the owning {@code agent_job} row, so a detection
 * job that crashes mid-run still has the calls it made on record and can be billed for them instead
 * of recording zero. Runs in its own {@code REQUIRES_NEW} transaction — the proxy servlet thread has
 * no ambient transaction, and the accounting write must commit independently of the passthrough.
 *
 * <p>The {@code agent_job} sink only. A mentor turn has no such row and accumulates into a
 * {@link MentorTurnMeter} instead; {@link ProxyAccounting} routes a served call to whichever of the
 * two its {@code BilledAttempt} names.
 *
 * <p>Never breaks the proxied response: an absent usage block records nothing, and a failed write is
 * caught rather than propagated.
 *
 * <p><b>Fenced to the attempt the call belongs to.</b> A provider call can outlive the attempt that
 * made it: orphan recovery requeues a job whose worker merely LOOKS dead, and that requeue zeroes
 * these very columns so the next attempt bills only its own calls. The write therefore carries the
 * attempt number read when the token was authenticated, and matches nothing once the row has moved
 * on — see {@link #recordSuperseded}.
 *
 * <p><b>But a failed write is money lost, not a no-op.</b> These running totals are not merely a
 * duplicate of the runner's report — {@code TerminalUsage.resolve} falls back to them precisely when
 * the runner produced no {@code usage.json}, which is the crashed-run case this class exists for. A
 * dropped accumulate on a run that then dies books a zero-token UNPRICED ledger event over calls that
 * really went out. So every failure is WARN-logged and counted as
 * {@code llm.proxy.usage.accumulate.failure}; a non-zero rate on that counter means the ledger is
 * under-billing.
 */
@Service
@ConditionalOnProperty(name = RuntimeRole.WORKER_PROPERTY, havingValue = "true", matchIfMissing = true)
public class ProxyUsageAccumulator {

    private static final Logger log = LoggerFactory.getLogger(ProxyUsageAccumulator.class);

    private final AgentJobRepository agentJobRepository;
    private final MeterRegistry meterRegistry;

    ProxyUsageAccumulator(AgentJobRepository agentJobRepository, MeterRegistry meterRegistry) {
        this.agentJobRepository = agentJobRepository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Add one served call's tokens to the attempt's running totals on its {@code agent_job} row.
     * Never throws — DB failures are warned, counted, and swallowed so accounting can never break the
     * proxied response.
     *
     * @param attempt the billing target, resolved when this call's token was authenticated; no-op when
     *     null (no live execution behind the call)
     * @param usage the call's tokens, already read off the upstream {@code usage} block; no-op when
     *     null (the provider reported none)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void accumulate(@Nullable BilledAttempt attempt, @Nullable ProxyTokenUsage usage) {
        if (attempt == null || usage == null) {
            return;
        }
        UUID jobId = attempt.sourceId();
        try {
            int rows = agentJobRepository.accumulateLlmUsage(
                jobId,
                attempt.number(),
                usage.billableInputTokens(),
                usage.outputTokens(),
                usage.reasoningTokens(),
                usage.cacheReadTokens()
            );
            if (rows == 0) {
                recordSuperseded(attempt);
            }
        } catch (RuntimeException e) {
            // Swallowed so the proxied response still reaches the runner, but never quietly: if this
            // job dies without a usage.json, these are the only tokens anyone would have billed.
            log.warn("Lost proxy usage accounting for job {} — this call may go unbilled", jobId, e);
            meterRegistry.counter("llm.proxy.usage.accumulate.failure").increment();
        }
    }

    /**
     * The attempt fence rejected this write: the row has moved on (requeued to a later attempt, or
     * already terminal) since the token was authenticated, so these tokens belong to a run that no
     * longer owns the row.
     *
     * <p>Dropping them under-bills by one call. Adding them anyway would be worse than under-billing:
     * the row's accumulators are what the NEXT attempt's terminal write bills, at the NEXT attempt's
     * frozen price and possibly from the other purse — so a late write would charge one attempt's
     * tokens to a different attempt, and potentially the instance for what a workspace's own provider
     * served. The ledger's {@code UNIQUE(source_type, source_id, source_attempt)} keeps attempts apart
     * once a row exists; this is the same rule applied one step earlier, to the mutable accumulator.
     *
     * <p>Counted rather than swallowed for the same reason the failure path is: a non-zero rate means
     * the ledger is under-billing, and a sustained one means orphan recovery is firing on workers that
     * are alive.
     */
    private void recordSuperseded(BilledAttempt attempt) {
        log.warn(
            "Dropping proxy usage from a superseded attempt — job {} is no longer running attempt {}; " +
                "this call goes unbilled rather than being charged to whoever owns the row now",
            attempt.sourceId(),
            attempt.number()
        );
        meterRegistry.counter("llm.proxy.usage.accumulate.superseded").increment();
    }
}
