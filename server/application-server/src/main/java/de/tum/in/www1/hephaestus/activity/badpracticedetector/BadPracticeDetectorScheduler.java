package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.leaderboard.LeaderboardTaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

@EnableScheduling
@Service
public class BadPracticeDetectorScheduler {

    private static final Logger logger = LoggerFactory.getLogger(LeaderboardTaskScheduler.class);

    @Value("${hephaestus.activity.schedule.day}")
    private String scheduledDay;

    @Value("${hephaestus.activity.schedule.time}")
    private String scheduledTime;

    @Autowired
    private ThreadPoolTaskScheduler taskScheduler;

    @Autowired
    private BadPracticeDetectorTask badPracticeDetectorTask;

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

        schedulePullRequestBadPracticeDetection(cron);
    }

    private void schedulePullRequestBadPracticeDetection(String cron) {
        taskScheduler.schedule(badPracticeDetectorTask, new CronTrigger("0 0 0 * * *"));
    }
}
