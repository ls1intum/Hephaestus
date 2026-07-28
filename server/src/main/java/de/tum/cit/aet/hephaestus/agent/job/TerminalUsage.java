package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult.LlmUsage;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageJobType;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * The token counts one ending attempt of an {@code AgentJob} is billed for, and whether they can be
 * trusted. Every terminal accounting path resolves its numbers here, so all of them answer "what did
 * this attempt spend?" the same way.
 *
 * <p>Two independent records of the same spend exist: the runner's {@code usage.json}, written only at
 * the end of a clean run but covering streamed calls too, and the LLM proxy's per-call accumulation
 * onto the {@code agent_job} row. The runner's is preferred where it exists; where it is missing or
 * empty the proxy's is the ONLY evidence of already-incurred spend, so the fallback is to the proxy,
 * never to zero.
 *
 * @param verifiable whether these numbers are real observed spend, or an admission that the attempt's
 *     spend is unknown — which the ledger records UNPRICED so the month reads unverifiable rather than
 *     cheap
 */
record TerminalUsage(
    long inputTokens,
    long outputTokens,
    long cacheReadTokens,
    long cacheWriteTokens,
    long reasoningTokens,
    int totalCalls,
    boolean verifiable
) {
    /**
     * @param runnerUsage what the agent runner reported, or {@code null} when it never produced one
     * @param proxyCounts the {@code agent_job} row's proxy accumulators, read BEFORE any requeue
     *     (which zeroes them) and BEFORE the runner's totals are written over them
     */
    static TerminalUsage resolve(@Nullable LlmUsage runnerUsage, @Nullable AgentJobLlmUsage proxyCounts) {
        if (hasTokens(runnerUsage)) {
            return new TerminalUsage(
                nullToZero(runnerUsage.inputTokens()),
                nullToZero(runnerUsage.outputTokens()),
                nullToZero(runnerUsage.cacheReadTokens()),
                nullToZero(runnerUsage.cacheWriteTokens()),
                nullToZero(runnerUsage.reasoningTokens()),
                runnerUsage.totalCalls(),
                true
            );
        }
        if (proxyCounts != null && proxyCounts.hasBillableUsage()) {
            return new TerminalUsage(
                proxyCounts.inputTokens(),
                proxyCounts.outputTokens(),
                proxyCounts.cacheReadTokens(),
                proxyCounts.cacheWriteTokens(),
                proxyCounts.reasoningTokens(),
                proxyCounts.totalCalls(),
                true
            );
        }
        // Neither record has tokens: keep whichever call count exists as telemetry, but report the
        // spend as unknown.
        int calls =
            runnerUsage != null && runnerUsage.totalCalls() > 0
                ? runnerUsage.totalCalls()
                : proxyCounts != null
                    ? Math.max(0, proxyCounts.totalCalls())
                    : 0;
        return new TerminalUsage(0L, 0L, 0L, 0L, 0L, calls, false);
    }

    /** An attempt that ended with nothing to bill — no runner report and no proxy accumulation. */
    static TerminalUsage none() {
        return resolve(null, null);
    }

    /**
     * Append this ending attempt to the spend ledger — the ONE way an {@code agent_job} becomes a ledger
     * row. Which of the recorder's two append paths runs is derived here, never chosen by the caller: a
     * caller picking it itself could write a PRICED row of invented zeros, the exact failure the ledger
     * exists to prevent.
     *
     * @param workspaceId passed explicitly rather than read off {@code job}: the terminal paths differ
     *     in whether they hold a workspace-scoped id or a job loaded with its workspace
     * @param upstreamModelId the model from the admitted snapshot — never a provider-reported name
     * @return true when the attempt was billed at its frozen price, false when it was appended UNPRICED
     */
    boolean appendTo(
        LlmUsageRecorder recorder,
        Long workspaceId,
        AgentJob job,
        @Nullable String upstreamModelId,
        LlmPriceSnapshot price
    ) {
        LlmUsageRecorder.LlmUsageSample sample = new LlmUsageRecorder.LlmUsageSample(
            LlmUsageJobType.from(job.getJobType()),
            LlmUsageSourceType.AGENT_JOB,
            job.getId(),
            job.getRetryCount(),
            upstreamModelId,
            inputTokens,
            outputTokens,
            cacheReadTokens,
            cacheWriteTokens,
            reasoningTokens,
            totalCalls,
            price,
            Instant.now()
        );
        if (verifiable && price.pricingState() != PricingState.UNPRICED) {
            recorder.record(workspaceId, sample);
            return true;
        }
        recorder.recordUnverifiable(workspaceId, sample);
        return false;
    }

    /** A report is evidence only if it claims a call AND a non-zero token bucket; calls alone cannot be priced. */
    private static boolean hasTokens(@Nullable LlmUsage usage) {
        return (
            usage != null &&
            usage.totalCalls() > 0 &&
            (nullToZero(usage.inputTokens()) > 0 ||
                nullToZero(usage.outputTokens()) > 0 ||
                nullToZero(usage.cacheReadTokens()) > 0 ||
                nullToZero(usage.cacheWriteTokens()) > 0 ||
                nullToZero(usage.reasoningTokens()) > 0)
        );
    }

    private static long nullToZero(@Nullable Integer value) {
        return value != null ? value : 0L;
    }
}
