package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.activity.model.BadPracticeDetection;
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

    private boolean sendBadPracticeDetectionEmail = true;

    @Override
    public void run() {
        BadPracticeDetection detectionResult = pullRequestBadPracticeDetector.detectBadPracticesForPullRequest(
            pullRequest
        );

        if (detectionResult.getBadPractices().isEmpty()) {
            return;
        }

        List<PullRequestBadPractice> badPractices = detectionResult.getBadPractices();

        List<PullRequestBadPractice> unResolvedBadPractices = badPractices
            .stream()
            .filter(badPractice -> !(badPractice.getState() == PullRequestBadPracticeState.FIXED))
            .filter(badPractice -> !(badPractice.getState() == PullRequestBadPracticeState.WONT_FIX))
            .filter(badPractice -> !(badPractice.getState() == PullRequestBadPracticeState.WRONG))
            .filter(badPractice -> !(badPractice.getState() == PullRequestBadPracticeState.GOOD_PRACTICE))
            .toList();

        List<PullRequestBadPractice> goodPractices = badPractices
            .stream()
            .filter(badPractice -> badPractice.getState() == PullRequestBadPracticeState.GOOD_PRACTICE)
            .toList();

        if (sendBadPracticeDetectionEmail && !unResolvedBadPractices.isEmpty()) {
            for (User user : pullRequest.getAssignees()) {
                mailService.sendBadPracticesDetectedInPullRequestEmail(user, pullRequest, unResolvedBadPractices, goodPractices);
            }
        }
    }
}
