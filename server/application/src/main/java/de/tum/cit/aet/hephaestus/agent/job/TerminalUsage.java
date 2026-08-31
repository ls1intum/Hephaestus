package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult.LlmUsage;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageJobType;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageSourceType;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.agent.usage.UsageProvenance;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The token counts one ending attempt of an {@code AgentJob} is billed for, and whether they can be
 * trusted. Every terminal accounting path resolves its numbers here, so all of them answer "what did
 * this attempt spend?" the same way.
 *
 * <p>Two independent records of the same spend exist and <b>neither is complete</b>:
 *
 * <ul>
 *   <li>the runner's {@code usage.json}, which covers streamed calls the proxy never parses, but is
 *       derived by walking the agent session's surviving messages — and compaction is on for every
 *       session, so every message it drops takes its tokens out of the report with it;
 *   <li>the LLM proxy's per-call accumulation onto the {@code agent_job} row, which never forgets a call
 *       it forwarded, but sees no streamed call and no cache write.
 * </ul>
 *
 * <p>So the attempt is billed the <b>per-bucket maximum</b> of the two. Preferring the runner outright
 * — which this did — meant a compacted session was billed only for what survived compaction, and a
 * live 29-practice review booked 26 of the 143 calls the proxy had already watched go upstream. Since
 * the budget cap reads this ledger, that is a cap computed from a quarter of the spend, not an
 * accounting nit.
 *
 * <p>The maximum is safe in both directions. Neither source double-counts within itself, so no bucket
 * can exceed the truth; and each covers calls the other misses, so the larger of the two is always at
 * least as close. Where only one source exists for a bucket — {@code cacheWriteTokens}, which the proxy
 * never accumulates — the maximum simply is that source's value. It can still under-bill, when both
 * sources lost the same call, which is why {@link #provenance()} is recorded rather than inferred.
 *
 * @param verifiable whether these numbers are real observed spend, or an admission that the attempt's
 *     spend is unknown — which the ledger records UNPRICED so the month reads unverifiable rather than
 *     cheap
 * @param provenance which record the billed numbers came from, so a later reader can tell a merged row
 *     from either source's own
 */
record TerminalUsage(
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens,
        long reasoningTokens,
        int totalCalls,
        boolean verifiable,
        UsageProvenance provenance) {
    /**
     * @param runnerUsage what the agent runner reported, or {@code null} when it never produced one
     * @param proxyCounts the {@code agent_job} row's proxy accumulators, read BEFORE any requeue
     *     (which zeroes them) and BEFORE the runner's totals are written over them
     */
    static TerminalUsage resolve(@Nullable LlmUsage runnerUsage, @Nullable AgentJobLlmUsage proxyCounts) {
        boolean fromRunner = hasTokens(runnerUsage);
        boolean fromProxy = proxyCounts != null && proxyCounts.hasBillableUsage();
        if (!fromRunner && !fromProxy) {
            // Neither record has tokens: keep whichever call count exists as telemetry, but report the
            // spend as unknown.
            int calls = runnerUsage != null && runnerUsage.totalCalls() > 0
                    ? runnerUsage.totalCalls()
                    : proxyCounts != null ? Math.max(0, proxyCounts.totalCalls()) : 0;
            return new TerminalUsage(0L, 0L, 0L, 0L, 0L, calls, false, UsageProvenance.NONE);
        }

        Buckets runner = fromRunner ? Buckets.of(Objects.requireNonNull(runnerUsage)) : Buckets.NONE;
        Buckets proxy = fromProxy ? Buckets.of(Objects.requireNonNull(proxyCounts)) : Buckets.NONE;
        return new TerminalUsage(
                Math.max(runner.input, proxy.input),
                Math.max(runner.output, proxy.output),
                Math.max(runner.cacheRead, proxy.cacheRead),
                Math.max(runner.cacheWrite, proxy.cacheWrite),
                Math.max(runner.reasoning, proxy.reasoning),
                // Clamped rather than cast: the call count is an int on the row, and a runner that reports
                // an absurd figure should bill the ceiling rather than wrap to a negative one.
                (int) Math.min(Integer.MAX_VALUE, Math.max(runner.calls, proxy.calls)),
                true,
                provenanceOf(runner, proxy));
    }

    /**
     * Which source the billed numbers came from. MERGED is not a rounding of "mostly one of them": it
     * means the row as stored matches neither source, so anyone reconciling against one of them will
     * find a discrepancy that is expected rather than a bug.
     */
    private static UsageProvenance provenanceOf(Buckets runner, Buckets proxy) {
        boolean proxyAddsSomething = proxy.exceedsAnyOf(runner);
        boolean runnerAddsSomething = runner.exceedsAnyOf(proxy);
        if (proxyAddsSomething && runnerAddsSomething) return UsageProvenance.MERGED;
        if (proxyAddsSomething) return UsageProvenance.PROXY;
        return UsageProvenance.RUNNER;
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
            LlmPriceSnapshot price) {
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
                provenance,
                Instant.now());
        if (verifiable && price.pricingState() != PricingState.UNPRICED) {
            recorder.record(workspaceId, sample);
            return true;
        }
        recorder.recordUnverifiable(workspaceId, sample);
        return false;
    }

    /** A report is evidence only if it claims a call AND a non-zero token bucket; calls alone cannot be priced. */
    private static boolean hasTokens(@Nullable LlmUsage usage) {
        return (usage != null
                && usage.totalCalls() > 0
                && (nullToZero(usage.inputTokens()) > 0
                        || nullToZero(usage.outputTokens()) > 0
                        || nullToZero(usage.cacheReadTokens()) > 0
                        || nullToZero(usage.cacheWriteTokens()) > 0
                        || nullToZero(usage.reasoningTokens()) > 0));
    }

    private static long nullToZero(@Nullable Integer value) {
        return value != null ? value : 0L;
    }

    /** One source's buckets, so the two can be compared without writing each bucket name five times. */
    private record Buckets(long input, long output, long cacheRead, long cacheWrite, long reasoning, long calls) {
        static final Buckets NONE = new Buckets(0, 0, 0, 0, 0, 0);

        static Buckets of(LlmUsage usage) {
            return new Buckets(
                    nullToZero(usage.inputTokens()),
                    nullToZero(usage.outputTokens()),
                    nullToZero(usage.cacheReadTokens()),
                    nullToZero(usage.cacheWriteTokens()),
                    nullToZero(usage.reasoningTokens()),
                    Math.max(0, usage.totalCalls()));
        }

        static Buckets of(AgentJobLlmUsage counts) {
            return new Buckets(
                    counts.inputTokens(),
                    counts.outputTokens(),
                    counts.cacheReadTokens(),
                    counts.cacheWriteTokens(),
                    counts.reasoningTokens(),
                    Math.max(0, counts.totalCalls()));
        }

        /** True when this source saw more than {@code other} in at least one bucket. */
        boolean exceedsAnyOf(Buckets other) {
            return (input > other.input
                    || output > other.output
                    || cacheRead > other.cacheRead
                    || cacheWrite > other.cacheWrite
                    || reasoning > other.reasoning
                    || calls > other.calls);
        }
    }
}
