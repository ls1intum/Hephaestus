package de.tum.cit.aet.hephaestus.agent.handler;

import de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationalDeliveryListener;
import de.tum.cit.aet.hephaestus.agent.handler.inapp.InAppCompositionListener;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.UnpreparedFeedbackLanes;
import de.tum.cit.aet.hephaestus.agent.metrics.AgentMetrics;
import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the two feedback-preparation lanes for finished jobs whose lanes never ran.
 *
 * <p><b>What it is for.</b> Both lanes hang off one {@code @Async @TransactionalEventListener}
 * (AFTER_COMMIT) event. That event is published once and never again; the pool it is submitted to is
 * bounded and rejects with {@code AbortPolicy} when full; and a rejection surfaces inside a transaction
 * synchronisation callback where no lane code runs, so nothing logs it as a loss. A single busy sync was
 * enough to throw thousands of rejections, and the only visible consequence was that some developers
 * silently got no feedback. Preparing late is fine — the practice pages and the mentor's queue are both
 * read days later — so the fix is to make "lost" into "late".
 *
 * <p><b>How it knows.</b> Not by looking for missing {@code feedback} rows: both lanes legitimately
 * prepare nothing very often, so absence of rows cannot distinguish "decided nothing" from "never ran"
 * and a sweep built on it would re-route every recent job on every pass, forever. Each lane instead
 * records its own completion on the job ({@code agent_job.in_chat_prepared_at} /
 * {@code in_app_prepared_at}) on every non-exceptional path, and this sweep is exactly the set of
 * finished jobs missing one of those marks.
 *
 * <p><b>Idempotence</b> is the preparers', not this class's: both write at deterministic
 * {@code (agent_job_id, position)} pairs and skip what {@code existsByAgentJobIdAndPosition} already
 * finds, so a recovered job re-derives the same units and writes none of them twice.
 *
 * <p>Shaped on {@link de.tum.cit.aet.hephaestus.agent.handler.conversation.ConversationFeedbackTtlSweeper}
 * — {@code @Scheduled} plus {@code @SchedulerLock} so one pod sweeps, server role only, per-job failures
 * isolated and counted rather than aborting the pass.
 */
@ConditionalOnServerRole
@Component
@WorkspaceAgnostic("Recovering unprepared feedback lanes across all workspaces on a bounded-lookback schedule")
public class FeedbackLanePreparationSweeper {

    private static final Logger log = LoggerFactory.getLogger(FeedbackLanePreparationSweeper.class);

    /**
     * How far back a pass looks. A lane left unprepared for longer than this is never recovered, which is
     * the deliberate trade: the alternative is a query with no lower bound that re-reads all of history
     * every hour to find the handful of rows an outage left behind. Comfortably longer than any pool
     * saturation the system has produced, and shorter than the practice pages' own reading rhythm.
     */
    static final Duration LOOKBACK = Duration.ofHours(24);

    /**
     * How recent a job may be and still be left alone. The listener owns the fast path; without this the
     * sweep would race a submission still sitting in the queue, and both would run the preparers at once
     * on the same job. They would not corrupt anything — the position guard holds — but one of them would
     * lose a unique-constraint race and log a failure that is not one.
     */
    static final Duration SETTLE = Duration.ofMinutes(10);

    /** Bounds one pass, so a backlog is worked off over several passes instead of in one long lock hold. */
    static final int MAX_JOBS_PER_PASS = 500;

    private final AgentJobRepository agentJobRepository;
    private final ConversationalDeliveryListener inChatLane;
    private final InAppCompositionListener inAppLane;
    private final MeterRegistry meterRegistry;

    public FeedbackLanePreparationSweeper(
            AgentJobRepository agentJobRepository,
            ConversationalDeliveryListener inChatLane,
            InAppCompositionListener inAppLane,
            MeterRegistry meterRegistry) {
        this.agentJobRepository = agentJobRepository;
        this.inChatLane = inChatLane;
        this.inAppLane = inAppLane;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(cron = "0 25 * * * *")
    @SchedulerLock(name = "feedback-lane-preparation-sweep", lockAtMostFor = "PT20M", lockAtLeastFor = "PT30S")
    public void sweep() {
        sweepNow(Instant.now());
    }

    /**
     * Recover every unprepared lane in the window ending {@code SETTLE} before {@code now}. Exposed so
     * tests can place the window around a fixture instead of waiting for the clock.
     *
     * @param now the reference instant; the window is {@code [now - LOOKBACK, now - SETTLE)}
     * @return what the pass found and what it managed to do about it
     */
    public SweepOutcome sweepNow(Instant now) {
        List<UnpreparedFeedbackLanes> pending = agentJobRepository.findUnpreparedFeedbackLanes(
                now.minus(LOOKBACK), now.minus(SETTLE), PageRequest.of(0, MAX_JOBS_PER_PASS));
        if (pending.isEmpty()) {
            return SweepOutcome.NOTHING;
        }
        int recovered = 0;
        int prepared = 0;
        int failed = 0;
        for (UnpreparedFeedbackLanes job : pending) {
            int unitsFromThisJob = 0;
            boolean anyLaneRan = false;
            boolean anyLaneFailed = false;
            if (job.inChatPending()) {
                LaneResult result = run(Lane.IN_CHAT, job);
                unitsFromThisJob += result.units();
                anyLaneRan |= result.ran();
                anyLaneFailed |= !result.ran();
            }
            if (job.inAppPending()) {
                LaneResult result = run(Lane.IN_APP, job);
                unitsFromThisJob += result.units();
                anyLaneRan |= result.ran();
                anyLaneFailed |= !result.ran();
            }
            if (anyLaneRan) {
                recovered++;
                prepared += unitsFromThisJob;
            }
            if (anyLaneFailed) {
                failed++;
            }
        }
        // Always logged when the pass found anything: a run of this sweeper observation work at all means the
        // listeners are dropping events, which is a fact about the async pool worth seeing in the log even
        // on the passes where every recovery succeeded.
        log.info(
                "feedback.lane.sweep: {} job(s) had an unprepared lane; recovered {}, prepared {} unit(s), {} still failing",
                pending.size(),
                recovered,
                prepared,
                failed);
        return new SweepOutcome(pending.size(), recovered, prepared, failed);
    }

    private LaneResult run(Lane lane, UnpreparedFeedbackLanes job) {
        try {
            int units =
                    switch (lane) {
                        case IN_CHAT -> inChatLane.prepare(job.agentJobId(), job.workspaceId());
                        case IN_APP -> inAppLane.prepare(job.agentJobId(), job.workspaceId());
                    };
            meterRegistry
                    .counter(AgentMetrics.FEEDBACK_LANE_SWEEP_RECOVERED, "lane", lane.tag)
                    .increment();
            return new LaneResult(true, units);
        } catch (RuntimeException e) {
            // The mark stays null, so the next pass tries again — until the job falls out of the lookback
            // window, at which point this counter is the only remaining evidence it was ever owed feedback.
            log.warn(
                    "feedback.lane.sweep: {} lane still failing for jobId={}: {}",
                    lane.tag,
                    job.agentJobId(),
                    e.toString());
            meterRegistry
                    .counter(AgentMetrics.FEEDBACK_LANE_SWEEP_FAILURE, "lane", lane.tag)
                    .increment();
            return new LaneResult(false, 0);
        }
    }

    private enum Lane {
        IN_CHAT("in-chat"),
        IN_APP("in-app");

        private final String tag;

        Lane(String tag) {
            this.tag = tag;
        }
    }

    private record LaneResult(boolean ran, int units) {}

    /**
     * @param found jobs in the window with at least one unprepared lane
     * @param recovered jobs where at least one lane ran to completion this pass
     * @param preparedUnits feedback units newly written by this pass
     * @param stillFailing jobs where at least one lane threw again
     */
    public record SweepOutcome(int found, int recovered, int preparedUnits, int stillFailing) {
        static final SweepOutcome NOTHING = new SweepOutcome(0, 0, 0, 0);
    }
}
