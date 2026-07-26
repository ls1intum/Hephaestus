package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * What ONE mentor turn has spent on the calls it has already completed — the budget gate's read model,
 * and nothing else.
 *
 * <p><b>It is not the billing record.</b> That is the turn's {@code chat_message} row, which
 * {@code MentorTurnUsageAccumulator} writes one call at a time and which every accounting path reads
 * back as a projection. This exists so that {@code ProxyBudgetGate}, which has to answer "what has this
 * turn spent?" on EVERY proxied call, does not need a query per call to do it: the same information is
 * already in this process, put here in the same step as the durable write.
 *
 * <p>The two can therefore only disagree in one direction, by construction: an entry is added here
 * only once the row write has COMMITTED — {@code MentorTurnUsageAccumulator} registers the mirror as
 * an {@code afterCommit} callback rather than performing it inline, so a transaction that fails at
 * commit takes the mirror with it. If the row write is fenced out, fails, or rolls back, nothing is
 * added here either. The meter can lag the row (a call served between two of this turn's own gate
 * checks, or one whose commit has not returned yet), but it can never claim spend the durable record
 * does not have. Lagging is the safe direction; over-claiming would refuse calls a workspace had
 * headroom for.
 *
 * <h2>Lifetime</h2>
 *
 * <p>Created by the turn, bound to the turn's sandbox session for the window in which that turn owns
 * the sandbox ({@code MentorProxyCredentialRegistry#bindTurn}/{@code unbindTurn}). Unbinding stops new
 * usage landing here. Nothing reads it after the turn ends, because by then the row is authoritative
 * and available.
 *
 * <h2>Why one CAS'd record and not four counters</h2>
 *
 * <p>{@link #spentUsd()} prices four numbers together. Four independent {@code AtomicLong}s could be
 * read mid-update and priced as a state that never existed (input from call 5, output from call 4).
 * A single {@link AtomicReference} over an immutable snapshot makes every read a state some call
 * actually produced.
 */
public final class MentorTurnMeter {

    /**
     * Tokens observed across the turn's completed calls.
     *
     * @param reasoningTokens already inside {@link #outputTokens}; reported, never priced twice
     */
    public record ObservedUsage(
        long inputTokens,
        long outputTokens,
        long reasoningTokens,
        long cacheReadTokens,
        int calls
    ) {
        static final ObservedUsage NONE = new ObservedUsage(0, 0, 0, 0, 0);

        /** True when no call has reported usage — the turn is unverifiable from the proxy's side. */
        public boolean isEmpty() {
            return inputTokens <= 0 && outputTokens <= 0 && cacheReadTokens <= 0;
        }

        ObservedUsage plus(ProxyTokenUsage call) {
            return new ObservedUsage(
                inputTokens + call.billableInputTokens(),
                outputTokens + call.outputTokens(),
                reasoningTokens + call.reasoningTokens(),
                cacheReadTokens + call.cacheReadTokens(),
                calls + 1
            );
        }
    }

    private final UUID turnId;

    /**
     * The price frozen onto the turn at admission — the same snapshot {@code MentorTurnPersistence}
     * bills the ledger with, so what the gate refuses a turn on cannot drift from what the turn is
     * eventually charged.
     */
    @Nullable
    private final LlmPriceSnapshot price;

    private final AtomicReference<ObservedUsage> observed = new AtomicReference<>(ObservedUsage.NONE);

    public MentorTurnMeter(UUID turnId, @Nullable LlmPriceSnapshot price) {
        this.turnId = turnId;
        this.price = price;
    }

    /** The {@code chat_message} id of the assistant turn this meter bills — the ledger's {@code source_id}. */
    public UUID turnId() {
        return turnId;
    }

    /** Add one completed call's tokens. Called on the proxy's request thread, once per served call. */
    void add(ProxyTokenUsage call) {
        observed.updateAndGet(current -> current.plus(call));
    }

    ObservedUsage observed() {
        return observed.get();
    }

    /**
     * What this turn's already-completed calls cost at the frozen rates. Reasoning tokens are
     * deliberately absent from the arguments — they are counted inside the output bucket, exactly as
     * {@code LlmUsageRecorder} prices a finished turn. {@link BigDecimal#ZERO} when the turn is
     * unpriced or nothing has been observed yet.
     */
    BigDecimal spentUsd() {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        ObservedUsage snapshot = observed.get();
        BigDecimal cost = price
            .calculateCost(snapshot.inputTokens(), snapshot.outputTokens(), snapshot.cacheReadTokens(), 0)
            .usd();
        return cost != null ? cost : BigDecimal.ZERO;
    }
}
