package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@ConditionalOnServerRole
@Component
@ConditionalOnBooleanProperty(RuntimeRole.AGENT_ENABLED_PROPERTY)
@WorkspaceAgnostic("Due schedules are opened for every workspace on the instance")
public class ReviewScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReviewScheduler.class);

    private static final int MAX_SCHEDULES_PER_TICK = 20;

    private final ReviewScheduleRepository scheduleRepository;
    private final ReviewScheduleBatchOpener opener;

    public ReviewScheduler(ReviewScheduleRepository scheduleRepository, ReviewScheduleBatchOpener opener) {
        this.scheduleRepository = scheduleRepository;
        this.opener = opener;
    }

    @Scheduled(fixedDelay = 5, initialDelay = 3, timeUnit = TimeUnit.MINUTES)
    @SchedulerLock(name = "review-schedule-scheduler", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    public void tick() {
        Instant now = Instant.now();
        List<ReviewSchedule> due = scheduleRepository.findDue(now, PageRequest.ofSize(MAX_SCHEDULES_PER_TICK));
        for (ReviewSchedule schedule : due) {
            try {
                ReviewScheduleOutcome outcome = opener.openDueRun(schedule.getId(), now);
                if (outcome != ReviewScheduleOutcome.OPENED) {
                    log.debug("Review schedule skipped: scheduleId={}, outcome={}", schedule.getId(), outcome);
                }
            } catch (RuntimeException e) {
                // The failed transaction rolled its own advance back, so without deferAfterFailure the
                // schedule stays due and would retry itself forever.
                log.warn(
                        "Review schedule failed to open, deferring to the next occurrence: scheduleId={}",
                        schedule.getId(),
                        e);
                try {
                    opener.deferAfterFailure(schedule.getId(), now);
                } catch (RuntimeException deferFailure) {
                    log.warn("Review schedule could not be deferred: scheduleId={}", schedule.getId(), deferFailure);
                }
            }
        }
    }
}
