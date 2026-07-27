package de.tum.cit.aet.hephaestus.agent.proxy;

import static de.tum.cit.aet.hephaestus.agent.usage.TransactionCallbacks.afterCommit;

import de.tum.cit.aet.hephaestus.agent.proxy.ProxyRouting.BilledAttempt;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.mentor.ChatMessageRepository;
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
 * Adds one proxied call's token usage to the mentor turn that made it — the mentor's counterpart to
 * {@link ProxyUsageAccumulator}, writing to the {@code chat_message} row instead of an
 * {@code agent_job} one. Runs in its own {@code REQUIRES_NEW} transaction: the proxy servlet thread
 * has no ambient transaction, and the accounting write must commit independently of the passthrough.
 *
 * <h2>Why the row and not just the in-memory meter</h2>
 *
 * <p>{@link MentorTurnMeter} is process-local, so a worker that dies takes the whole turn's spend with
 * it and {@code MentorInFlightReaper} was left booking a zero-token UNVERIFIABLE event over calls that
 * really went out. The row survives that. The meter still exists, but only as the budget gate's read
 * model (see its class doc) — and it is advanced ONLY after the row write COMMITTED, so it can never
 * claim spend the durable record does not have.
 *
 * <h2>The fence</h2>
 *
 * <p>{@code status = 'in_flight'} in {@code ChatMessageRepository#accumulateLlmUsage} is what keeps
 * turns apart, and it is stronger than the in-process binding: a call carrying turn A's id can never
 * be added to turn B's row, whatever is bound at the time. A write that matches no row means the turn
 * ended while this call was in flight; those tokens are dropped rather than charged to whoever is
 * running now, which is the same trade the job path makes for a superseded attempt — under-bill by one
 * call rather than mis-bill it.
 *
 * <p><b>But a dropped write is money lost, not a no-op.</b> Every drop and every failure is
 * WARN-logged and counted, because these columns are the only record of a crashed turn's calls: a
 * non-zero rate on {@code llm.proxy.usage.mentor.superseded} or
 * {@code llm.proxy.usage.mentor.failure} means the ledger is under-billing.
 */
@Service
@ConditionalOnProperty(name = RuntimeRole.WORKER_PROPERTY, havingValue = "true", matchIfMissing = true)
public class MentorTurnUsageAccumulator {

    private static final Logger log = LoggerFactory.getLogger(MentorTurnUsageAccumulator.class);

    private final ChatMessageRepository chatMessageRepository;
    private final MentorProxyCredentialRegistry credentialRegistry;
    private final MeterRegistry meterRegistry;

    MentorTurnUsageAccumulator(
        ChatMessageRepository chatMessageRepository,
        MentorProxyCredentialRegistry credentialRegistry,
        MeterRegistry meterRegistry
    ) {
        this.chatMessageRepository = chatMessageRepository;
        this.credentialRegistry = credentialRegistry;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record one served call against the turn's durable totals, then mirror it onto the turn's meter so
     * the budget gate sees it on the next call.
     *
     * <p>Order matters: the row is the authoritative record and the meter is derived from it, so the
     * meter is advanced only once a write that actually landed has COMMITTED. Never throws — a failed
     * write is warned, counted, and swallowed so accounting can never break the proxied response.
     *
     * @param attempt the billing target resolved when this call's token was authenticated; no-op when
     *     null (no turn was running)
     * @param usage the call's tokens; no-op when null (the provider reported none)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void accumulate(@Nullable BilledAttempt attempt, @Nullable ProxyTokenUsage usage) {
        if (attempt == null || usage == null) {
            return;
        }
        UUID turnId = attempt.sourceId();
        try {
            int rows = chatMessageRepository.accumulateLlmUsage(
                turnId,
                usage.billableInputTokens(),
                usage.outputTokens(),
                usage.reasoningTokens(),
                usage.cacheReadTokens()
            );
            if (rows == 0) {
                recordSuperseded(turnId);
                return;
            }
            // AFTER COMMIT, not here: inside the transaction the UPDATE has only been SENT. A commit
            // that then fails would leave the meter holding spend the row never got — the one direction
            // MentorTurnMeter's contract forbids, since the gate would refuse calls the workspace had
            // headroom for. Post-commit the row is durable, so the mirror can only lag it.
            //
            // Best-effort mirror for the gate. A miss here is NOT an error and is not counted: it means
            // the turn stopped owning its sandbox while this call was in flight (the client-disconnect
            // drain), so nothing will consult the meter again anyway. The money is already on the row.
            afterCommit(() -> credentialRegistry.accumulate(turnId, usage));
        } catch (RuntimeException e) {
            // Swallowed so the proxied response still reaches the runner, but never quietly: if this
            // turn now dies without a runner report, these are the only tokens anyone would have billed.
            log.warn("Lost proxy usage accounting for mentor turn {} — this call may go unbilled", turnId, e);
            meterRegistry.counter("llm.proxy.usage.mentor.failure").increment();
        }
    }

    /**
     * The fence rejected this write: the turn went terminal (finalised, interrupted, or reaped) after
     * this call was authenticated, so its totals have already been read and billed. Adding to them now
     * would either double-bill a turn that is done or, if the row were reused, charge one turn's tokens
     * to another. Dropping under-bills by one call, which is the lesser error — the same choice
     * {@code ProxyUsageAccumulator} makes for a superseded job attempt.
     */
    private void recordSuperseded(UUID turnId) {
        log.warn(
            "Dropping proxy usage from a finished mentor turn — turn {} is no longer in flight; " +
                "this call goes unbilled rather than being added to a turn that has already been billed",
            turnId
        );
        meterRegistry.counter("llm.proxy.usage.mentor.superseded").increment();
    }
}
