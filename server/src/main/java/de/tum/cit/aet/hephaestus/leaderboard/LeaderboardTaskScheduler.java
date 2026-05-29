package de.tum.cit.aet.hephaestus.leaderboard;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.leaderboard.tasks.LeaguePointsUpdateTask;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * Wires every registered {@link LeaderboardNotificationTask} + the league-points update
 * onto the cron defined in {@link LeaderboardProperties}.
 *
 * <p>Notification tasks register themselves as Spring beans implementing the marker
 * interface — the scheduler picks them up via constructor injection and stays vendor-
 * agnostic. The Slack task lives in the slack module; future Teams/Discord/email tasks
 * register the same way without changing this class.
 */
@Order(value = Ordered.LOWEST_PRECEDENCE)
@Component
@Profile("!test")
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
public class LeaderboardTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardTaskScheduler.class);

    // Cron fires on every server replica; ShedLock ensures only one replica runs each task per
    // tick (lockAtLeastFor covers clock skew so a fast task can't release before peers fire).
    private static final Duration LOCK_AT_MOST_FOR = Duration.ofHours(1);
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ofMinutes(1);

    private final LeaderboardProperties leaderboardProperties;
    private final TaskScheduler taskScheduler;
    private final List<LeaderboardNotificationTask> notificationTasks;
    private final LeaguePointsUpdateTask leaguePointsUpdateTask;
    private final LockProvider lockProvider;

    public LeaderboardTaskScheduler(
        LeaderboardProperties leaderboardProperties,
        TaskScheduler taskScheduler,
        List<LeaderboardNotificationTask> notificationTasks,
        LeaguePointsUpdateTask leaguePointsUpdateTask,
        LockProvider lockProvider
    ) {
        this.leaderboardProperties = leaderboardProperties;
        this.taskScheduler = taskScheduler;
        this.notificationTasks = notificationTasks;
        this.leaguePointsUpdateTask = leaguePointsUpdateTask;
        this.lockProvider = lockProvider;
    }

    /**
     * Wraps a task so that, across multiple server replicas, only the replica that acquires the
     * named ShedLock executes it on a given tick. Mirrors the {@code @SchedulerLock} guard the
     * SCM sync schedulers use — but applied programmatically because the leaderboard cron is built
     * dynamically from {@link LeaderboardProperties} rather than a static {@code @Scheduled} cron.
     */
    private Runnable locked(Runnable task, String lockName) {
        return () -> {
            Optional<SimpleLock> lock = lockProvider.lock(
                new LockConfiguration(Instant.now(), lockName, LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR)
            );
            if (lock.isEmpty()) {
                log.info("Skipped leaderboard task: reason=lockHeldByAnotherReplica, lock={}", lockName);
                return;
            }
            try {
                task.run();
            } finally {
                lock.get().unlock();
            }
        };
    }

    @EventListener(ApplicationReadyEvent.class)
    public void activateTaskScheduler() {
        var timeParts = leaderboardProperties.schedule().time().split(":");

        String cron = String.format(
            "0 %s %s ? * %s",
            timeParts.length > 1 ? timeParts[1] : 0,
            timeParts[0],
            leaderboardProperties.schedule().day()
        );

        if (!CronExpression.isValidExpression(cron)) {
            log.error("Rejected invalid cron expression: cronExpression={}", cron);
            return;
        }

        scheduleNotificationTasks(cron);
        scheduleLeaguePointsUpdate(cron);
    }

    private void scheduleNotificationTasks(String cron) {
        if (!leaderboardProperties.notification().enabled()) {
            log.info("Skipped notification task scheduling: reason=notificationsDisabled");
            return;
        }
        if (notificationTasks.isEmpty()) {
            log.warn("Skipped notification task scheduling: reason=noTasksRegistered");
            return;
        }

        for (LeaderboardNotificationTask task : notificationTasks) {
            String description = task.getClass().getSimpleName();
            log.info("Scheduled notification task: task={}, cronExpression={}", description, cron);
            scheduleSafely(locked(task, "leaderboard-notify-" + description), new CronTrigger(cron), description);
        }
    }

    private void scheduleLeaguePointsUpdate(String cron) {
        log.info("Scheduled league points update: cronExpression={}", cron);
        scheduleSafely(
            locked(leaguePointsUpdateTask, "leaderboard-league-points-update"),
            new CronTrigger(cron),
            "league points update"
        );
    }

    private void scheduleSafely(Runnable task, CronTrigger trigger, String description) {
        try {
            taskScheduler.schedule(task, trigger);
        } catch (TaskRejectedException ex) {
            log.warn("Skipped scheduling: reason=taskRejected, task={}", description, ex);
        }
    }
}
