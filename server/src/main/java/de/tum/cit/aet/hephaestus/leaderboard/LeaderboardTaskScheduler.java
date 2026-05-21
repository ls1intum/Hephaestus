package de.tum.cit.aet.hephaestus.leaderboard;

import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.leaderboard.tasks.LeaguePointsUpdateTask;
import de.tum.cit.aet.hephaestus.leaderboard.tasks.SlackWeeklyLeaderboardTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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

/** Wires the weekly Slack message + league-points update onto the cron defined in {@link LeaderboardProperties}. */
@Order(value = Ordered.LOWEST_PRECEDENCE)
@Component
@Profile("!test")
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
public class LeaderboardTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardTaskScheduler.class);

    private final LeaderboardProperties leaderboardProperties;
    private final TaskScheduler taskScheduler;
    private final ObjectProvider<SlackWeeklyLeaderboardTask> slackWeeklyLeaderboardTaskProvider;
    private final LeaguePointsUpdateTask leaguePointsUpdateTask;

    public LeaderboardTaskScheduler(
        LeaderboardProperties leaderboardProperties,
        TaskScheduler taskScheduler,
        ObjectProvider<SlackWeeklyLeaderboardTask> slackWeeklyLeaderboardTaskProvider,
        LeaguePointsUpdateTask leaguePointsUpdateTask
    ) {
        this.leaderboardProperties = leaderboardProperties;
        this.taskScheduler = taskScheduler;
        this.slackWeeklyLeaderboardTaskProvider = slackWeeklyLeaderboardTaskProvider;
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

        scheduleSlackMessage(cron);
        scheduleLeaguePointsUpdate(cron);
    }

    private void scheduleSlackMessage(String cron) {
        if (!leaderboardProperties.notification().enabled()) {
            log.info("Skipped Slack message scheduling: reason=notificationsDisabled");
            return;
        }

        SlackWeeklyLeaderboardTask task = slackWeeklyLeaderboardTaskProvider.getIfAvailable();
        if (task == null) {
            log.warn("Skipped Slack message scheduling: reason=beanNotAvailable");
            return;
        }

        if (!task.testSlackConnection()) {
            log.error("Failed to schedule Slack message: reason=connectionTestFailed");
            return;
        }

        log.info("Scheduled Slack message: cronExpression={}", cron);
        scheduleSafely(task, new CronTrigger(cron), "Slack weekly leaderboard message");
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
