package de.tum.cit.aet.hephaestus.leaderboard;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.leaderboard.tasks.LeaguePointsUpdateTask;
import java.util.List;
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

    private final LeaderboardProperties leaderboardProperties;
    private final TaskScheduler taskScheduler;
    private final List<LeaderboardNotificationTask> notificationTasks;
    private final LeaguePointsUpdateTask leaguePointsUpdateTask;

    public LeaderboardTaskScheduler(
        LeaderboardProperties leaderboardProperties,
        TaskScheduler taskScheduler,
        List<LeaderboardNotificationTask> notificationTasks,
        LeaguePointsUpdateTask leaguePointsUpdateTask
    ) {
        this.leaderboardProperties = leaderboardProperties;
        this.taskScheduler = taskScheduler;
        this.notificationTasks = notificationTasks;
        this.leaguePointsUpdateTask = leaguePointsUpdateTask;
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
            scheduleSafely(task, new CronTrigger(cron), description);
        }
    }

    private void scheduleLeaguePointsUpdate(String cron) {
        log.info("Scheduled league points update: cronExpression={}", cron);
        scheduleSafely(leaguePointsUpdateTask, new CronTrigger(cron), "league points update");
    }

    private void scheduleSafely(Runnable task, CronTrigger trigger, String description) {
        try {
            taskScheduler.schedule(task, trigger);
        } catch (TaskRejectedException ex) {
            log.warn("Skipped scheduling: reason=taskRejected, task={}", description, ex);
        }
    }
}
