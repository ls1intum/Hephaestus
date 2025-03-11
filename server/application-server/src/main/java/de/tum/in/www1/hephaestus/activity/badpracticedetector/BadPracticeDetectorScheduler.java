package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class BadPracticeDetectorScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BadPracticeDetectorScheduler.class);

    private static final String READY_TO_MERGE = "ready to merge";

    @Qualifier("applicationTaskExecutor")
    @Autowired
    TaskExecutor taskExecutor;

    public void detectBadPracticeForPrIfReadyToMerge(
        PullRequest pullRequest,
        Set<Label> oldLabels,
        Set<Label> newLabels
    ) {
        logger.info("Checking if bad practice detection should be scheduled for pull request: {}", pullRequest.getId());
        logger.info("Old labels: {}", oldLabels);
        logger.info("New labels: {}", newLabels);
        if (
            newLabels.stream().anyMatch(label -> READY_TO_MERGE.equals(label.getName())) &&
            oldLabels.stream().noneMatch(label -> READY_TO_MERGE.equals(label.getName()))
        ) {
            logger.info("Scheduling bad practice detection for pull request: {}", pullRequest.getId());
            BadPracticeDetectorTask badPracticeDetectorTask = new BadPracticeDetectorTask();
            badPracticeDetectorTask.setPullRequest(pullRequest);
            taskExecutor.execute(badPracticeDetectorTask);
        }
    }
}
