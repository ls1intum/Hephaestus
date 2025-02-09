package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class BadPracticeDetectorScheduler {

    private final String readyToMerge = "ready to merge";


    public void detectBadPracticeForPrIfReadyToMerge(PullRequest pullRequest, Set<Label> oldLabels, Set<Label> newLabels) {

        if (newLabels.stream().anyMatch(label -> readyToMerge.equals(label.getName())) && oldLabels.stream().noneMatch(label -> readyToMerge.equals(label.getName()))) {

            BadPracticeDetectorTask badPracticeDetectorTask = new BadPracticeDetectorTask();
            badPracticeDetectorTask.setPullRequest(pullRequest);
            Thread thread = new Thread(badPracticeDetectorTask);
            thread.start();
        }
    }
}
