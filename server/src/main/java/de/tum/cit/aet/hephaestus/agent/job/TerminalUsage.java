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
 * trusted. Every terminal accounting path — clean completion, cancellation, worker drain,
 * infra-retry, the zombie sweeper's reaper — resolves its numbers here, so all of them answer "what
 * did this attempt spend?" the same way.
 *
 * <h2>Two independent records of the same spend</h2>
 *
 * <p>The runner writes its own {@code usage.json} at the end of a clean run, and the LLM proxy
 * accumulates each non-streaming call onto the {@code agent_job} row as the run happens
 * ({@code AgentJobRepository#accumulateLlmUsage}). The runner's report is preferred when it exists:
 * it also covers streamed calls, which the proxy accumulator skips.
 *
 * <p>But a runner that dies, is killed mid-write, or emits a malformed/empty {@code usage.json} is
 * exactly the case where the proxy's record is the ONLY evidence of real, already-incurred spend.
 * Reading the runner's report alone there would book a zero-token event over calls the proxy watched
 * go out — the workspace's month would understate what the instance actually paid for, and a budget
 * cap computed from that ledger would let the overspend continue. So an absent or empty runner
 * report falls back to the proxy accumulators rather than to zero.
 *
 * @param verifiable whether these numbers are real observed spend ({@code true}) or an admission
 *     that the attempt's spend is unknown ({@code false}), which the ledger records UNPRICED so the
 *     month is flagged unverifiable instead of quietly reading as cheap
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
     * Pick the authoritative token counts for an attempt that is ending.
     *
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
        // Neither record has tokens. Keep whichever call count exists as telemetry, but say plainly
        // that the spend is unknown.
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
     * Append this ending attempt to the spend ledger — the ONE way an {@code agent_job} becomes a
     * ledger row, so no terminal path can assemble the row differently from the others.
     *
     * <p>Which of the recorder's two append paths runs is derived here rather than chosen by the
     * caller, because both inputs to that choice live here: an attempt is billed at its frozen price
     * only when the token counts are real observed spend AND a price was actually resolved. Anything
     * else is appended UNPRICED, which is what makes the workspace's month read <em>unverifiable</em>
     * instead of cheap. A caller that picked the path itself could produce a PRICED row of invented
     * zeros — the exact failure the ledger exists to prevent.
     *
     * <p>The resolved token counts are carried onto an UNPRICED row unchanged. They cost nothing to
     * keep (the row's cost is null either way) and they are the only surviving record of how much the
     * provider was actually asked to do.
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

    /**
     * A usage report is only evidence if it claims a call AND at least one non-zero token bucket. A
     * runner that reports calls with all-zero tokens has told us nothing we can price.
     */
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
