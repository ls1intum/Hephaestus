package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class BadPracticeDetectorTask implements Runnable {

    @Autowired
    private PullRequestBadPracticeDetector pullRequestBadPracticeDetector;

    private PullRequest pullRequest;

    @Override
    public void run() {
        pullRequestBadPracticeDetector.detectAndSyncBadPractices(pullRequest);
    }
}
