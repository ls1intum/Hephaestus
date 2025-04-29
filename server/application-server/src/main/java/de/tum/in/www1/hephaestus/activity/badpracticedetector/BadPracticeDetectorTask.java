package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeState;
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

        List<PullRequestBadPractice> unResolvedBadPractices = badPractices
            .stream()
            .filter(badPractice -> !(badPractice.getState() == PullRequestBadPracticeState.FIXED))
            .filter(badPractice -> !(badPractice.getState() == PullRequestBadPracticeState.WONT_FIX))
            .filter(badPractice -> !(badPractice.getState() == PullRequestBadPracticeState.WRONG))
            .toList();

        if (!unResolvedBadPractices.isEmpty()) {
            for (User user : pullRequest.getAssignees()) {
                mailService.sendBadPracticesDetectedInPullRequestEmail(user, pullRequest, unResolvedBadPractices);
            }
        }
    }
}
