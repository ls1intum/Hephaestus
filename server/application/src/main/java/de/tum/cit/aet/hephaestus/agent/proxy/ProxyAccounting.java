package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
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

/** Everything the LLM proxy records or decides about a request's cost. */
// Gated like the worker-only accumulators it depends on; an ungated bean here would fail the context
// of every tier that runs with the worker role off.
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
            ObjectMapper objectMapper) {
        this.budgetGate = budgetGate;
        this.usageAccumulator = usageAccumulator;
        this.mentorTurnUsageAccumulator = mentorTurnUsageAccumulator;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Whether the payer of THIS call has crossed their monthly cap, counting the calling attempt's own
     * spend, which the ledger cannot see until the run ends.
     */
    public boolean refuseForBudget(ProxyRouting routing) {
        if (!budgetGate.isBlocked(routing)) {
            return false;
        }
        meterRegistry
                .counter("llm.proxy.budget.blocked", "apiProtocol", routing.apiProtocol())
                .increment();
        return true;
    }

    /**
     * A credential authenticated but named no execution to bill. The rate is a defect signal, not a
     * usage statistic: a sandbox is calling outside the window its turn owns.
     */
    public void recordUnbillableRefusal(String apiProtocol) {
        meterRegistry
                .counter("llm.proxy.unbillable.refused", "apiProtocol", apiProtocol)
                .increment();
    }

    /**
     * Never throws: a body this cannot read must not turn a call the provider already served — and
     * already charged us for — into an error for the runner. Counted instead, since an unreadable body
     * means this call's tokens were billed to nobody.
     */
    public void recordUsage(ProxyRouting.BilledAttempt attempt, byte[] upstreamBody, boolean responsesProtocol) {
        try {
            JsonNode parsed = objectMapper.readTree(upstreamBody);
            recordUsage(attempt, ProxyTokenUsage.from(parsed, responsesProtocol));
        } catch (Exception e) {
            log.warn("Could not parse upstream usage for {} — this call's tokens go unbilled", attempt.sourceId(), e);
            recordMalformedUsage(attempt);
        }
    }

    public void recordUsage(ProxyRouting.BilledAttempt attempt, @Nullable ProxyTokenUsage usage) {
        if (usage == null) {
            return;
        }
        switch (attempt.sourceType()) {
            case AGENT_JOB -> usageAccumulator.accumulate(attempt, usage);
            case MENTOR_TURN -> mentorTurnUsageAccumulator.accumulate(attempt, usage);
        }
    }

    public void recordMalformedUsage(ProxyRouting.BilledAttempt attempt) {
        meterRegistry
                .counter(
                        "llm.proxy.usage.unparseable",
                        "sourceType",
                        attempt.sourceType().name())
                .increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void stopTimer(Timer.Sample sample, String apiProtocol) {
        sample.stop(Timer.builder(AgentMetrics.LLM_PROXY_DURATION)
                .description("LLM proxy request duration")
                .tag("apiProtocol", apiProtocol)
                .register(meterRegistry));
    }

    public void recordError(String apiProtocol) {
        meterRegistry.counter("llm.proxy.errors", "apiProtocol", apiProtocol).increment();
    }

    /**
     * An upstream refused the streamed-usage request the proxy adds, so its tokens cannot be read off
     * the stream. A sustained rate means every streamed call on that connection is under-billed.
     */
    public void recordStreamUsageUnsupported(String apiProtocol) {
        meterRegistry
                .counter("llm.proxy.stream.usage.unsupported", "apiProtocol", apiProtocol)
                .increment();
    }
}
