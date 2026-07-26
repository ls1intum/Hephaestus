package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Everything the LLM proxy records or decides about a request's cost: whether the workspace
 * may still spend, what the call actually consumed, and how the hop performed.
 *
 * <p>These three concerns travel together — a blocked call is counted, a served call is accumulated,
 * and both are timed — so they live behind one collaborator instead of three constructor parameters
 * on {@link LlmProxyController}, which must stay a thin forwarding surface.
 */
// Same worker gate as LlmProxyController and ProxyUsageAccumulator: this sits between them, so an
// ungated bean here would demand the worker-only accumulator in every tier that runs with the worker
// role off (server, webhook) and fail their context on startup.
@Component
@ConditionalOnProperty(name = RuntimeRole.WORKER_PROPERTY, havingValue = "true", matchIfMissing = true)
public class ProxyAccounting {

    private static final Logger log = LoggerFactory.getLogger(ProxyAccounting.class);

    private final ProxyBudgetGate budgetGate;
    private final ProxyUsageAccumulator usageAccumulator;
    private final MentorTurnUsageAccumulator mentorTurnUsageAccumulator;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    ProxyAccounting(
        ProxyBudgetGate budgetGate,
        ProxyUsageAccumulator usageAccumulator,
        MentorTurnUsageAccumulator mentorTurnUsageAccumulator,
        MeterRegistry meterRegistry,
        ObjectMapper objectMapper
    ) {
        this.budgetGate = budgetGate;
        this.usageAccumulator = usageAccumulator;
        this.mentorTurnUsageAccumulator = mentorTurnUsageAccumulator;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Whether the payer of THIS call has crossed their monthly cap — counting what the calling attempt
     * has already spent, which the ledger cannot see until the run ends — so it must be refused. Reads
     * a short-TTL cached ledger verdict, so this is not a per-call month-window SUM. Counts the refusal
     * itself, so "blocked" is observable without the caller touching a registry.
     */
    public boolean refuseForBudget(ProxyRouting routing) {
        if (!budgetGate.isBlocked(routing)) {
            return false;
        }
        meterRegistry.counter("llm.proxy.budget.blocked", "apiProtocol", routing.apiProtocol()).increment();
        return true;
    }

    /**
     * Attribute a served NON-STREAMING call's tokens to the execution that made it. Never throws: a
     * body this cannot read must not turn a call the provider already served — and already charged us
     * for — into an error for the runner.
     *
     * <p>But it is not free either. A 2xx body that is not the JSON we expect means this call's tokens
     * were never billed to anyone, so it is WARNed and counted on
     * {@code llm.proxy.usage.unparseable} exactly as the accumulators count their own dropped writes:
     * a non-zero rate means the ledger is under-billing, and the fix is a gateway or protocol change
     * rather than a retry.
     */
    public void recordUsage(ProxyRouting.BilledAttempt attempt, byte[] upstreamBody, boolean responsesProtocol) {
        try {
            JsonNode parsed = objectMapper.readTree(upstreamBody);
            recordUsage(attempt, ProxyTokenUsage.from(parsed, responsesProtocol));
        } catch (Exception e) {
            log.warn("Could not parse upstream usage for {} — this call's tokens go unbilled", attempt.sourceId(), e);
            meterRegistry.counter("llm.proxy.usage.unparseable", "sourceType", attempt.sourceType().name()).increment();
        }
    }

    /**
     * Attribute one served call's already-parsed tokens to the execution that made it — the shared
     * tail of the buffered and streamed paths.
     *
     * <p>Routes by the attempt's source type, because the two kinds of execution keep their running
     * totals on different rows: an agent job on its {@code agent_job} row, a mentor turn on its
     * {@code chat_message} one. Both writes are fenced on the execution's identity and both survive
     * the worker that made the call, and a fenced-out write is COUNTED rather than swallowed: a
     * non-zero rate means the ledger is under-billing.
     */
    public void recordUsage(ProxyRouting.BilledAttempt attempt, @Nullable ProxyTokenUsage usage) {
        if (usage == null) {
            return;
        }
        switch (attempt.sourceType()) {
            case AGENT_JOB -> usageAccumulator.accumulate(attempt, usage);
            case MENTOR_TURN -> mentorTurnUsageAccumulator.accumulate(attempt, usage);
        }
    }

    /** Start timing one proxied request; stop the returned sample with {@link #stopTimer}. */
    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void stopTimer(Timer.Sample sample, String apiProtocol) {
        sample.stop(
            Timer.builder("llm.proxy.duration")
                .description("LLM proxy request duration")
                .tag("apiProtocol", apiProtocol)
                .register(meterRegistry)
        );
    }

    public void recordError(String apiProtocol) {
        meterRegistry.counter("llm.proxy.errors", "apiProtocol", apiProtocol).increment();
    }

    /**
     * An upstream refused the streamed-usage request the proxy adds, so the call was retried without
     * it and its tokens cannot be read off the stream. Counted rather than only logged because a
     * sustained rate means every streamed call against that connection is under-billed, and the fix is
     * a provider or model change, not a code one.
     */
    public void recordStreamUsageUnsupported(String apiProtocol) {
        meterRegistry.counter("llm.proxy.stream.usage.unsupported", "apiProtocol", apiProtocol).increment();
    }
}
