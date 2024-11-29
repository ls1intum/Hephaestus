package de.tum.in.www1.hephaestus.leaderboard;

import de.tum.in.www1.hephaestus.leaderboard.tasks.SlackWeeklyLeaderboardTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

@Order(value = Ordered.LOWEST_PRECEDENCE)
@EnableScheduling
@Service
public class LeaderboardTaskScheduler {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardTaskScheduler.class);

    @Value("${hephaestus.leaderboard.notification.enabled}")
    private boolean runScheduledMessage;

    @Value("${hephaestus.leaderboard.schedule.day}")
    private String scheduledDay;

    @Value("${hephaestus.leaderboard.schedule.time}")
    private String scheduledTime;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    private SlackWeeklyLeaderboardTask slackWeeklyLeaderboardTask;


    @EventListener(ApplicationReadyEvent.class)
    public void activateTaskScheduler() {

        var timeParts = scheduledTime.split(":");

        // CRON for the end of every leaderboard cycle
        String cron = String.format(
            "0 %s %s ? * %s",
            timeParts.length > 1 ? timeParts[1] : 0,
            timeParts[0],
            scheduledDay
        );

        if (!CronExpression.isValidExpression(cron)) {
            logger.error("Invalid cron expression: " + cron);
            return;
        }

        scheduleSlackMessage(cron);
        
    }

    /**
     * Schedule a Slack message to be sent at the end of every leaderboard cycle.
     */
    private void scheduleSlackMessage(String cron) {
        if (!runScheduledMessage) return;

        if (!slackWeeklyLeaderboardTask.testSlackConnection()) {
            logger.error("Failed to schedule Slack message");
            return;
        }

        logger.info("Scheduling Slack message to run with {}", cron);
        taskScheduler.schedule(slackWeeklyLeaderboardTask, new CronTrigger(cron));
    }
}
