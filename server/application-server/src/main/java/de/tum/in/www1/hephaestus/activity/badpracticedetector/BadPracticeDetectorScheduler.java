package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.notification.MailService;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class BadPracticeDetectorScheduler {

    private static final Logger logger = LoggerFactory.getLogger(BadPracticeDetectorScheduler.class);

    private static final String READY_TO_REVIEW = "ready to review";
    private static final String READY_TO_MERGE = "ready to merge";

    @Qualifier("applicationTaskExecutor")
    @Autowired
    TaskExecutor taskExecutor;

    @Autowired
    PullRequestBadPracticeDetector pullRequestBadPracticeDetector;

    @Autowired
    MailService mailService;

    @Value("${hephaestus.detection.automatic-detection-enabled}")
    private boolean automaticDetectionEnabled;

    public void detectBadPracticeForPrIfReady(
        PullRequest pullRequest,
        Set<Label> oldLabels,
        Set<Label> newLabels
    ) {
        if (
            automaticDetectionEnabled &&
            newLabels.stream().anyMatch(label -> READY_TO_REVIEW.equals(label.getName())) &&
            oldLabels.stream().noneMatch(label -> READY_TO_REVIEW.equals(label.getName()))
        ) {
            BadPracticeDetectorTask badPracticeDetectorTask = new BadPracticeDetectorTask();
            badPracticeDetectorTask.setPullRequest(pullRequest);
            taskExecutor.execute(badPracticeDetectorTask);
        } else if (
            automaticDetectionEnabled &&
            newLabels.stream().anyMatch(label -> READY_TO_MERGE.equals(label.getName())) &&
            oldLabels.stream().noneMatch(label -> READY_TO_MERGE.equals(label.getName()))
        ) {
            logger.info(
                "Scheduling bad practice detection for pull request because it is ready to merge: {}",
                pullRequest.getId()
            );
            BadPracticeDetectorTask badPracticeDetectorTask = new BadPracticeDetectorTask();
            badPracticeDetectorTask.setPullRequestBadPracticeDetector(pullRequestBadPracticeDetector);
            badPracticeDetectorTask.setMailService(mailService);
            badPracticeDetectorTask.setPullRequest(pullRequest);
            taskExecutor.execute(badPracticeDetectorTask);
        }
    }
}
