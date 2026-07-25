package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
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
 * Everything the LLM proxy records or decides about a request's cost (#1368): whether the workspace
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
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    ProxyAccounting(
        ProxyBudgetGate budgetGate,
        ProxyUsageAccumulator usageAccumulator,
        MeterRegistry meterRegistry,
        ObjectMapper objectMapper
    ) {
        this.budgetGate = budgetGate;
        this.usageAccumulator = usageAccumulator;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * Whether the payer of THIS call has crossed their monthly cap, so it must be refused.
     * Reads a short-TTL cached verdict, so this is not a per-call month-window SUM. Counts the
     * refusal itself, so "blocked" is observable without the caller touching a registry.
     */
    public boolean refuseForBudget(Long workspaceId, @Nullable FundingSource fundingSource, String apiProtocol) {
        if (!budgetGate.isBlocked(workspaceId, fundingSource)) {
            return false;
        }
        meterRegistry.counter("llm.proxy.budget.blocked", "apiProtocol", apiProtocol).increment();
        return true;
    }

    /**
     * Attribute a served call's tokens to the job that made it. Best-effort by design: an unparseable
     * or absent usage block records nothing and never affects the response the caller returns.
     */
    public void recordUsage(java.util.UUID sourceId, byte[] upstreamBody, boolean responsesProtocol) {
        try {
            JsonNode parsed = objectMapper.readTree(upstreamBody);
            usageAccumulator.accumulate(sourceId, parsed, responsesProtocol);
        } catch (Exception e) {
            log.debug("Could not parse upstream usage for job {}: {}", sourceId, e.getMessage());
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
}
