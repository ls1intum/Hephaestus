package de.tum.in.www1.hephaestus.practices.detection;

import de.tum.in.www1.hephaestus.practices.model.BadPracticeDetection;
import de.tum.in.www1.hephaestus.practices.model.DetectionResult;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPractice;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPracticeState;
import de.tum.in.www1.hephaestus.practices.spi.BadPracticeNotificationSender;
import de.tum.in.www1.hephaestus.shared.badpractice.BadPracticeInfo;
import de.tum.in.www1.hephaestus.shared.badpractice.BadPracticeNotificationData;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scheduled task that runs bad practice detection for a pull request.
 *
 * <p>Stores only the pull request ID and pre-extracted scalar data (assignee logins,
 * repository name, PR metadata) instead of the detached JPA entity. This avoids
 * {@code LazyInitializationException} when the task executes outside the original
 * Hibernate session (e.g., after a 1-hour delay via {@code TaskScheduler}).
 *
 * <p>Detection is performed by calling {@code detectAndSyncBadPractices(pullRequestId)}
 * which re-fetches the entity within its own {@code @Transactional} boundary.
 */
@Getter
@Setter
public class BadPracticeDetectorTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(BadPracticeDetectorTask.class);

    private PullRequestBadPracticeDetector pullRequestBadPracticeDetector;

    private BadPracticeNotificationSender notificationSender;

    /** The pull request ID — used to re-fetch within a transactional boundary at execution time. */
    private Long pullRequestId;

    /** Pre-extracted scalar data for notifications (avoids lazy entity access). */
    private int pullRequestNumber;

    private String pullRequestTitle;
    private String pullRequestHtmlUrl;
    private String repositoryName;
    private List<String> assigneeLogins;

    private boolean sendBadPracticeDetectionEmail = true;

    private String workspaceSlug;

    @Override
    public void run() {
        DetectionResult result = pullRequestBadPracticeDetector.detectAndSyncBadPractices(pullRequestId);

        if (result != DetectionResult.BAD_PRACTICES_DETECTED) {
            return;
        }

        if (!sendBadPracticeDetectionEmail || assigneeLogins == null || assigneeLogins.isEmpty()) {
            return;
        }

        // Re-fetch the detection to get the bad practices for notification
        BadPracticeDetection detection = pullRequestBadPracticeDetector.getLatestDetection(pullRequestId);
        if (detection == null || detection.getBadPractices().isEmpty()) {
            return;
        }

        List<PullRequestBadPractice> unResolvedBadPractices = detection
            .getBadPractices()
            .stream()
            .filter(badPractice -> !(badPractice.getState() == PullRequestBadPracticeState.FIXED))
            .filter(badPractice -> !(badPractice.getState() == PullRequestBadPracticeState.WONT_FIX))
            .filter(badPractice -> !(badPractice.getState() == PullRequestBadPracticeState.WRONG))
            .filter(badPractice -> !(badPractice.getState() == PullRequestBadPracticeState.GOOD_PRACTICE))
            .toList();

        if (unResolvedBadPractices.isEmpty()) {
            return;
        }

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

        for (String assigneeLogin : assigneeLogins) {
            BadPracticeNotificationData notificationData = new BadPracticeNotificationData(
                assigneeLogin,
                null, // email will be fetched from Keycloak
                pullRequestNumber,
                pullRequestTitle,
                pullRequestHtmlUrl,
                repositoryName,
                workspaceSlug,
                badPracticeInfos
            );
            notificationSender.sendBadPracticeNotification(notificationData);
        }
    }
}
