package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * What ONE mentor turn has spent on the calls it has already completed — the budget gate's read model,
 * and nothing else. The billing record is the turn's {@code chat_message} row; this exists so the gate
 * need not query it on every proxied call.
 *
 * <p>A call is added here only once the row write has committed, so the meter may lag the row but can
 * never claim spend the durable record does not have. Over-claiming would refuse calls a workspace had
 * headroom for.
 *
 * <p>One CAS'd snapshot rather than four counters because {@link #spentUsd()} prices four numbers
 * together, and independent counters could be read mid-update and priced as a state no call produced.
 */
public final class MentorTurnMeter {

    /**
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

    /** The same snapshot the ledger is billed with, so the gate and the charge cannot drift. */
    @Nullable
    private final LlmPriceSnapshot price;

    private final AtomicReference<ObservedUsage> observed = new AtomicReference<>(ObservedUsage.NONE);

    public MentorTurnMeter(UUID turnId, @Nullable LlmPriceSnapshot price) {
        this.turnId = turnId;
        this.price = price;
    }

    /** The assistant {@code chat_message} id — the ledger's {@code source_id}. */
    public UUID turnId() {
        return turnId;
    }

    void add(ProxyTokenUsage call) {
        observed.updateAndGet(current -> current.plus(call));
    }

    ObservedUsage observed() {
        return observed.get();
    }

    /** Reasoning tokens are deliberately absent: they are counted inside the output bucket. */
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
