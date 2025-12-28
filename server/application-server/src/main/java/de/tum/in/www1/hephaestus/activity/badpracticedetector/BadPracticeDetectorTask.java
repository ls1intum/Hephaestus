package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.activity.model.BadPracticeDetection;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeState;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.notification.MailService;
import de.tum.in.www1.hephaestus.shared.badpractice.BadPracticeInfo;
import de.tum.in.www1.hephaestus.shared.badpractice.BadPracticeNotificationData;
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

    private String workspaceSlug;

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

        if (sendBadPracticeDetectionEmail && !unResolvedBadPractices.isEmpty()) {
            // Convert entities to DTOs to break circular dependency
            List<BadPracticeInfo> badPracticeInfos = unResolvedBadPractices
                .stream()
                .map(bp ->
                    new BadPracticeInfo(
                        bp.getId(),
                        bp.getTitle(),
                        bp.getDescription(),
                        bp.getState() != null ? bp.getState().name() : null
                    )
                )
                .toList();

            for (User user : pullRequest.getAssignees()) {
                BadPracticeNotificationData notificationData = new BadPracticeNotificationData(
                    user.getLogin(),
                    null, // email will be fetched from Keycloak
                    pullRequest.getNumber(),
                    pullRequest.getTitle(),
                    pullRequest.getHtmlUrl(),
                    pullRequest.getRepository() != null ? pullRequest.getRepository().getName() : null,
                    workspaceSlug,
                    badPracticeInfos
                );
                mailService.sendBadPracticesDetectedInPullRequestEmail(notificationData);
            }
        }
    }
}
