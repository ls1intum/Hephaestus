package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Opens a campaign for every sweep schedule that has come due.
 *
 * <p>The last way into the review path that needs nothing to have happened first. Every other one waits
 * for an event, a reconciliation, a person asking or an admin scoping a campaign — so an artifact that
 * nothing ever announced has no ledger row at all, and "we never looked at this" is indistinguishable
 * from "we looked and declined". This closes that: a workspace with a schedule has a row for every
 * artifact in the window either way.
 *
 * <p>Deliberately thin. It selects due rows, hands each to {@link ReviewSweepCampaignOpener} and logs;
 * the pacing, the budget checks, the cursor and the per-artifact isolation are all
 * {@code ReviewBackfillDriver}'s, already written, already tested.
 *
 * <p>Under a lock like {@code PendingSignalReaper}, and for the same reason: two replicas that both
 * opened a run for the same due schedule would be settled by the campaign-under-way check, but only
 * after both had enumerated and priced a scope.
 */
@ConditionalOnServerRole
@Component
@ConditionalOnProperty(prefix = "hephaestus.agent", name = "enabled", havingValue = "true")
@WorkspaceAgnostic("Due schedules are opened for every workspace on the instance")
public class ReviewSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReviewSweepScheduler.class);

    /**
     * How many schedules one tick opens. Each becomes a campaign that the driver then paces, and the
     * driver advances at most five campaigns per tick — so pulling more than this in would only build a
     * queue of runs waiting on a driver that is already saturated.
     */
    private static final int MAX_SCHEDULES_PER_TICK = 20;

    private final ReviewSweepScheduleRepository scheduleRepository;
    private final ReviewSweepCampaignOpener opener;

    public ReviewSweepScheduler(ReviewSweepScheduleRepository scheduleRepository, ReviewSweepCampaignOpener opener) {
        this.scheduleRepository = scheduleRepository;
        this.opener = opener;
    }

    /**
     * Every five minutes rather than nightly. The cadence a schedule keeps is its own
     * {@code nextRunAt}; this only has to notice one has passed, and noticing within five minutes is what
     * lets an admin who has just created a schedule watch it work.
     */
    @Scheduled(fixedDelay = 5, initialDelay = 3, timeUnit = TimeUnit.MINUTES)
    @SchedulerLock(name = "review-sweep-scheduler", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void tick() {
        Instant now = Instant.now();
        List<ReviewSweepSchedule> due = scheduleRepository.findDue(now, PageRequest.ofSize(MAX_SCHEDULES_PER_TICK));
        for (ReviewSweepSchedule schedule : due) {
            try {
                ReviewSweepOutcome outcome = opener.openDueRun(schedule.getId(), now);
                if (outcome != ReviewSweepOutcome.OPENED) {
                    log.debug("Review sweep skipped: scheduleId={}, outcome={}", schedule.getId(), outcome);
                }
            } catch (RuntimeException e) {
                // One workspace's sweep must not stop the rest, and it must not retry itself to death
                // either: the failed transaction rolled its own advance back, so the schedule is still
                // due and would be picked up again in five minutes, for ever.
                log.warn(
                    "Review sweep failed to open, deferring to the next occurrence: scheduleId={}",
                    schedule.getId(),
                    e
                );
                try {
                    opener.deferAfterFailure(schedule.getId(), now);
                } catch (RuntimeException deferFailure) {
                    log.warn("Review sweep could not be deferred: scheduleId={}", schedule.getId(), deferFailure);
                }
            }
        }
    }
}
