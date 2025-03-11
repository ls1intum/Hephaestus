package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.notification.MailService;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BadPracticeDetectorTask implements Runnable {

    private PullRequestBadPracticeDetector pullRequestBadPracticeDetector;

    private MailService mailService;

    private PullRequest pullRequest;

    @Override
    public void run() {
        List<PullRequestBadPractice> badPractices = pullRequestBadPracticeDetector.detectAndSyncBadPractices(
            pullRequest
        );

        if (!badPractices.isEmpty()) {
            for (User user : pullRequest.getAssignees()) {
                mailService.sendBadPracticesDetectedInPullRequestEmail(user, pullRequest, badPractices);
            }
        }
    }
}
