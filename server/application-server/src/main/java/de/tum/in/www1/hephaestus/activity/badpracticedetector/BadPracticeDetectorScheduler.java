package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
public class BadPracticeDetectorScheduler {

    private static final String READY_TO_REVIEW = "ready to review";
    private static final String READY_TO_MERGE = "ready to merge";

    @Qualifier("applicationTaskExecutor")
    @Autowired
    TaskExecutor taskExecutor;

    public void detectBadPracticeForPrIfReady(
        PullRequest pullRequest,
        Set<Label> oldLabels,
        Set<Label> newLabels
    ) {
        if (
            newLabels.stream().anyMatch(label -> READY_TO_REVIEW.equals(label.getName())) &&
            oldLabels.stream().noneMatch(label -> READY_TO_REVIEW.equals(label.getName()))
        ) {
            BadPracticeDetectorTask badPracticeDetectorTask = new BadPracticeDetectorTask();
            badPracticeDetectorTask.setPullRequest(pullRequest);
            taskExecutor.execute(badPracticeDetectorTask);
        } else if (
            newLabels.stream().anyMatch(label -> READY_TO_MERGE.equals(label.getName())) &&
            oldLabels.stream().noneMatch(label -> READY_TO_MERGE.equals(label.getName()))
        ) {
            BadPracticeDetectorTask badPracticeDetectorTask = new BadPracticeDetectorTask();
            badPracticeDetectorTask.setPullRequest(pullRequest);
            taskExecutor.execute(badPracticeDetectorTask);
        }
    }
}
