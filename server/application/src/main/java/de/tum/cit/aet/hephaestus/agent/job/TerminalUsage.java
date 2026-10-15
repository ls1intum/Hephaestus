package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.agent.config.ConfigSnapshot;
import de.tum.cit.aet.hephaestus.agent.config.FrozenModel;
import de.tum.cit.aet.hephaestus.agent.runtime.AgentResult.LlmUsage;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.LlmUsageRecorder;
import de.tum.cit.aet.hephaestus.agent.usage.MeasurementVerification;
import de.tum.cit.aet.hephaestus.agent.usage.UsageProvenance;
import de.tum.cit.aet.hephaestus.agent.usage.UsageSourceType;
import de.tum.cit.aet.hephaestus.integration.core.spi.UsagePricingState;
import de.tum.cit.aet.hephaestus.practices.spi.ModelKind;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Retains one observed token vector for an ending attempt. Prompt and output totals select the source,
 * with the proxy winning ties. Incomparable totals remain unverifiable instead of combining buckets.
 * Aggregate totals do not establish that either source observed every call.
 * Measurement verification follows source selection, independently of the frozen price.
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
     * @param proxyCounts the {@code review_job} row's proxy accumulators, read BEFORE any requeue
     *     (which zeroes them) and BEFORE the runner's totals are written over them
     * @param practiceScript the attempt's practice script totals by model kind. Its chat total is added to the
     *     runner's report: its session events contain no direct calls, while the proxy's counters do.
     *     Its buckets are those of the proxy's counters, so its cache reads and writes keep their own rates.
     */
    static TerminalUsage resolve(
            @Nullable LlmUsage runnerUsage,
            @Nullable ReviewJobLlmUsage proxyCounts,
            List<PracticeScriptKindTotal> practiceScript) {
        boolean fromRunner = hasTokens(runnerUsage);
        boolean fromProxy = proxyCounts != null && proxyCounts.hasReportedUsage();
        if (!fromRunner && !fromProxy) {
            // Neither record has tokens: keep whichever call count exists as telemetry, but report the
            // spend as unknown.
            int calls = runnerUsage != null && runnerUsage.totalCalls() > 0
                    ? runnerUsage.totalCalls()
                    : proxyCounts != null ? Math.max(0, proxyCounts.totalCalls()) : 0;
            return new TerminalUsage(0L, 0L, 0L, 0L, 0L, calls, false, UsageProvenance.NONE);
        }

        Buckets runner = fromRunner ? Buckets.of(Objects.requireNonNull(runnerUsage)) : Buckets.NONE;
        if (fromRunner) {
            for (PracticeScriptKindTotal total : practiceScript) {
                if (total.modelKind() == ModelKind.CHAT) {
                    runner = runner.plus(total);
                }
            }
        }
        Buckets proxy = fromProxy ? Buckets.of(Objects.requireNonNull(proxyCounts)) : Buckets.NONE;
        boolean proxyCovers = proxy.covers(runner);
        boolean runnerCovers = !proxyCovers && runner.covers(proxy);
        Buckets selected = runnerCovers ? runner : proxy;
        return new TerminalUsage(
                selected.input,
                selected.output,
                selected.cacheRead,
                selected.cacheWrite,
                selected.reasoning,
                // Call counts are telemetry, not coverage. Clamped rather than cast: the count is an int on the
                // row, and an absurd report should store the ceiling rather than wrap to a negative one.
                (int) Math.min(Integer.MAX_VALUE, Math.max(runner.calls, proxy.calls)),
                (proxyCovers || runnerCovers) && chatUsageReported(practiceScript),
                runnerCovers ? UsageProvenance.RUNNER : UsageProvenance.PROXY);
    }

    /**
     * An attempt that ended without a runner report. The proxy's counters already hold its practice script
     * chat calls.
     */
    static TerminalUsage fromProxy(@Nullable ReviewJobLlmUsage proxyCounts) {
        return resolve(null, proxyCounts, List.of());
    }

    /** An attempt that ended with nothing to bill — no runner report and no proxy accumulation. */
    static TerminalUsage none() {
        return fromProxy(null);
    }

    /**
     * Append this ending attempt to the spend ledger — the ONE way an {@code review_job} attempt becomes
     * ledger rows: its own row, then one row for each model for practice scripts other than chat that it called,
     * over all its practices. The ledger key holds no practice, so one row per practice would keep only
     * the first.
     * The practice script chat calls are already in this attempt's own row: the proxy's counters hold them, and
     * {@link #resolve} adds them to a runner report. Which of the recorder's two
     * append paths a row takes is derived here, never chosen by the caller: a caller picking it itself
     * could write a PRICED row of invented zeros, the exact failure the ledger exists to prevent.
     *
     * @param workspaceId passed explicitly rather than read off {@code job}: the terminal paths differ
     *     in whether they hold a workspace-scoped id or a job loaded with its workspace
     * @param snapshot the admitted snapshot, which names the models and prices the practice script rows;
     *     {@code null} when it cannot be read, which leaves the practice script rows UNPRICED
     * @param price the price of the attempt's own row
     * @return true when the attempt's own row was billed at its frozen price, false when it was appended
     *     UNPRICED
     */
    boolean appendTo(
            LlmUsageRecorder recorder,
            ReviewJobRepository jobs,
            Long workspaceId,
            ReviewJob job,
            @Nullable ConfigSnapshot snapshot,
            LlmPriceSnapshot price) {
        // One instant for every row of the attempt, so midnight on the 1st cannot split it across two months.
        Instant occurredAt = Instant.now();
        List<PracticeScriptKindTotal> practiceScript =
                jobs.sumPracticeScriptUsageByKind(workspaceId, job.getId(), job.getAttempt());
        boolean billed = append(
                recorder,
                workspaceId,
                new LlmUsageRecorder.LlmUsageSample(
                        job.getReviewId(),
                        "PRACTICE_REVIEW",
                        UsageSourceType.REVIEW_JOB,
                        job.getId(),
                        job.getAttempt(),
                        snapshot == null ? null : snapshot.model().upstreamModelId(),
                        inputTokens,
                        outputTokens,
                        cacheReadTokens,
                        cacheWriteTokens,
                        reasoningTokens,
                        0L,
                        totalCalls,
                        price,
                        provenance,
                        verifiable && chatUsageReported(practiceScript)
                                ? MeasurementVerification.VERIFIED
                                : MeasurementVerification.UNVERIFIABLE,
                        occurredAt));
        for (PracticeScriptKindTotal total : practiceScript) {
            if (total.modelKind() == ModelKind.CHAT) {
                continue;
            }
            FrozenModel model = snapshot == null ? null : snapshot.practiceScriptSlot(total.modelKind());
            LlmPriceSnapshot frozen = model == null ? null : model.priceSnapshot();
            append(
                    recorder,
                    workspaceId,
                    new LlmUsageRecorder.LlmUsageSample(
                            job.getReviewId(),
                            "PRACTICE_REVIEW",
                            ScriptUsage.ledgerSourceType(total.modelKind()),
                            job.getId(),
                            job.getAttempt(),
                            model == null ? null : model.upstreamModelId(),
                            total.inputTokens(),
                            total.outputTokens(),
                            total.cacheReadTokens(),
                            total.cacheWriteTokens(),
                            0L,
                            total.searchUnits(),
                            (int) Math.min(Integer.MAX_VALUE, total.calls()),
                            frozen != null ? frozen : LlmPriceSnapshot.unpricedInstance(),
                            UsageProvenance.PROXY,
                            total.hasCompleteUsage()
                                    ? MeasurementVerification.VERIFIED
                                    : MeasurementVerification.UNVERIFIABLE,
                            occurredAt));
        }
        return billed;
    }

    // Only verified measurements may use the frozen price.
    private static boolean append(LlmUsageRecorder recorder, Long workspaceId, LlmUsageRecorder.LlmUsageSample sample) {
        recorder.record(workspaceId, sample);
        return sample.measurementVerification() == MeasurementVerification.VERIFIED
                && sample.price().pricingState() != UsagePricingState.UNPRICED;
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

    private static boolean chatUsageReported(List<PracticeScriptKindTotal> totals) {
        return totals.stream()
                .filter(total -> total.modelKind() == ModelKind.CHAT)
                .allMatch(PracticeScriptKindTotal::hasCompleteUsage);
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

        static Buckets of(ReviewJobLlmUsage counts) {
            return new Buckets(
                    counts.inputTokens(),
                    counts.outputTokens(),
                    counts.cacheReadTokens(),
                    counts.cacheWriteTokens(),
                    counts.reasoningTokens(),
                    Math.max(0, counts.totalCalls()));
        }

        Buckets plus(PracticeScriptKindTotal total) {
            return new Buckets(
                    input + total.inputTokens(),
                    output + total.outputTokens(),
                    cacheRead + total.cacheReadTokens(),
                    cacheWrite + total.cacheWriteTokens(),
                    reasoning,
                    calls + total.calls());
        }

        long prompt() {
            return input + cacheRead + cacheWrite;
        }

        /** True when this source's prompt and output totals are both at least {@code other}'s. */
        boolean covers(Buckets other) {
            return prompt() >= other.prompt() && output >= other.output;
        }
    }
}
