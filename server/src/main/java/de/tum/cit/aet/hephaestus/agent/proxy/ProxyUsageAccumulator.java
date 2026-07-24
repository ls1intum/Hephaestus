package de.tum.cit.aet.hephaestus.agent.proxy;

import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Adds one proxied call's token usage to the owning {@code agent_job} row (#1368), so a detection
 * job that crashes mid-run still has the calls it made on record and can be billed for them instead
 * of recording zero. Runs in its own {@code REQUIRES_NEW} transaction — the proxy servlet thread has
 * no ambient transaction, and the accounting write must commit independently of the passthrough.
 *
 * <p>Best-effort by design: a malformed or absent usage block simply records nothing. The happy path
 * is unaffected because the job's terminal write overwrites these running totals with the
 * runner-reported authoritative usage, so there is still exactly one ledger writer and no
 * double-count.
 */
@Service
@ConditionalOnProperty(
    name = de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole.WORKER_PROPERTY,
    havingValue = "true",
    matchIfMissing = true
)
public class ProxyUsageAccumulator {

    private static final Logger log = LoggerFactory.getLogger(ProxyUsageAccumulator.class);

    private final AgentJobRepository agentJobRepository;

    ProxyUsageAccumulator(AgentJobRepository agentJobRepository) {
        this.agentJobRepository = agentJobRepository;
    }

    /**
     * Parse the {@code usage} block of a non-streaming upstream response and add it to the job's
     * totals. Never throws — parse/DB failures are logged and swallowed so accounting can never break
     * the proxied response.
     *
     * @param jobId the billing target ({@code ProxyRouting.sourceId}); no-op when null (mentor route)
     * @param responseBody the full upstream JSON response
     * @param responsesProtocol true for the {@code /responses} shape, false for chat-completions
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void accumulate(UUID jobId, JsonNode responseBody, boolean responsesProtocol) {
        if (jobId == null || responseBody == null) {
            return;
        }
        try {
            JsonNode usage = responseBody.get("usage");
            if (usage == null || !usage.isObject()) {
                return;
            }
            int input;
            int output;
            int reasoning;
            int cacheRead;
            if (responsesProtocol) {
                input = usage.path("input_tokens").asInt(0);
                output = usage.path("output_tokens").asInt(0);
                cacheRead = usage.path("input_tokens_details").path("cached_tokens").asInt(0);
                reasoning = usage.path("output_tokens_details").path("reasoning_tokens").asInt(0);
            } else {
                input = usage.path("prompt_tokens").asInt(0);
                output = usage.path("completion_tokens").asInt(0);
                cacheRead = usage.path("prompt_tokens_details").path("cached_tokens").asInt(0);
                reasoning = usage.path("completion_tokens_details").path("reasoning_tokens").asInt(0);
            }
            // Upstream reports the prompt-token count inclusive of cached tokens; the ledger's input
            // bucket is the non-cached remainder so cache reads are not billed twice.
            int billableInput = Math.max(0, input - cacheRead);
            agentJobRepository.accumulateLlmUsage(jobId, billableInput, output, reasoning, cacheRead, 0);
        } catch (RuntimeException e) {
            log.debug("Could not accumulate proxy usage for job {}: {}", jobId, e.getMessage());
        }
    }
}
