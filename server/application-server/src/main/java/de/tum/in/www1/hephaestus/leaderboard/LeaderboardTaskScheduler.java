package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.leaderboard.tasks.LeaguePointsUpdateTask;
import de.tum.in.www1.hephaestus.leaderboard.tasks.SlackWeeklyLeaderboardTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

/**
 * Schedules tasks to run at the end of every leaderboard cycle.
 * @see SlackWeeklyLeaderboardTask
 */
@Order(value = Ordered.LOWEST_PRECEDENCE)
@Component
public class LeaderboardTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardTaskScheduler.class);

    private final LeaderboardProperties leaderboardProperties;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final SlackWeeklyLeaderboardTask slackWeeklyLeaderboardTask;
    private final LeaguePointsUpdateTask leaguePointsUpdateTask;

    public LeaderboardTaskScheduler(
        LeaderboardProperties leaderboardProperties,
        ThreadPoolTaskScheduler taskScheduler,
        @Autowired(required = false) SlackWeeklyLeaderboardTask slackWeeklyLeaderboardTask,
        LeaguePointsUpdateTask leaguePointsUpdateTask
    ) {
        this.leaderboardProperties = leaderboardProperties;
        this.taskScheduler = taskScheduler;
        this.slackWeeklyLeaderboardTask = slackWeeklyLeaderboardTask;
        this.leaguePointsUpdateTask = leaguePointsUpdateTask;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void activateTaskScheduler() {
        // Schedule always on app ready; guard per-task below
        var timeParts = leaderboardProperties.schedule().time().split(":");

        // CRON for the end of every leaderboard cycle
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

        scheduleSlackMessage(cron);
        scheduleLeaguePointsUpdate(cron);
    }

    /**
     * Schedule a Slack message to be sent at the end of every leaderboard cycle.
     */
    private void scheduleSlackMessage(String cron) {
        if (!leaderboardProperties.notification().enabled()) {
            log.info("Skipped Slack message scheduling: reason=notificationsDisabled");
            return;
        }

        if (slackWeeklyLeaderboardTask == null) {
            log.warn("Skipped Slack message scheduling: reason=beanNotAvailable");
            return;
        }

        if (!slackWeeklyLeaderboardTask.testSlackConnection()) {
            log.error("Failed to schedule Slack message: reason=connectionTestFailed");
            return;
        }

        log.info("Scheduled Slack message: cronExpression={}", cron);
        scheduleSafely(slackWeeklyLeaderboardTask, new CronTrigger(cron), "Slack weekly leaderboard message");
    }

    private void scheduleLeaguePointsUpdate(String cron) {
        log.info("Scheduled league points update: cronExpression={}", cron);
        scheduleSafely(leaguePointsUpdateTask, new CronTrigger(cron), "league points update");
    }

    private void scheduleSafely(Runnable task, CronTrigger trigger, String description) {
        if (isSchedulerShuttingDown()) {
            log.info("Skipped scheduling: reason=schedulerShuttingDown, task={}", description);
            return;
        }

        try {
            taskScheduler.schedule(task, trigger);
        } catch (TaskRejectedException ex) {
            log.warn("Skipped scheduling: reason=taskRejected, task={}", description, ex);
        }
    }

    private boolean isSchedulerShuttingDown() {
        ScheduledThreadPoolExecutor executor = taskScheduler.getScheduledThreadPoolExecutor();
        return executor == null || executor.isShutdown() || executor.isTerminating();
    }
}
