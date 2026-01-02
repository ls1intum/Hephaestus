package de.tum.in.www1.hephaestus.practices.detection;

import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Event listener that triggers bad practice detection on PR events.
 *
 * <p>Listens for PR created/updated events and routes them to the
 * BadPracticeDetectorScheduler which handles:
 * <ul>
 *   <li>1-hour delay for new/ready-for-review PRs to allow authors to fix issues</li>
 *   <li>Keycloak role checks (run_automatic_detection) when not running for all</li>
 *   <li>Email notifications when bad practices are detected</li>
 *   <li>Task cancellation when PR is updated (resets the delay)</li>
 * </ul>
 */
@Component
public class BadPracticeEventListener {

    private static final Logger logger = LoggerFactory.getLogger(BadPracticeEventListener.class);

    private final BadPracticeDetectorScheduler badPracticeDetectorScheduler;
    private final PullRequestRepository pullRequestRepository;

    public BadPracticeEventListener(
        BadPracticeDetectorScheduler badPracticeDetectorScheduler,
        PullRequestRepository pullRequestRepository
    ) {
        this.badPracticeDetectorScheduler = badPracticeDetectorScheduler;
        this.pullRequestRepository = pullRequestRepository;
    }

    /**
     * Handles PR created events.
     * Schedules detection with 1-hour delay and email notifications enabled.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestCreated(DomainEvent.PullRequestCreated event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        logger.info(
            "PR #{} created in {} - scheduling bad practice detection with 1-hour delay",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        scheduleDetectionWithDelay(pr.id(), pr.number());
    }

    /**
     * Handles PR updated events.
     * Reschedules detection with 1-hour delay (cancels previous scheduled task).
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestUpdated(DomainEvent.PullRequestUpdated event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        logger.info(
            "PR #{} updated in {} - rescheduling bad practice detection with 1-hour delay",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        scheduleDetectionWithDelay(pr.id(), pr.number());
    }

    /**
     * Handles PR labeled events.
     * Only triggers for ready-related labels (exact match), runs immediately with email notifications.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestLabeled(DomainEvent.PullRequestLabeled event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        String labelName = event.label() != null ? event.label().name() : "";
        logger.info(
            "PR #{} labeled with '{}' in {} - checking for ready label detection",
            pr.number(),
            labelName,
            pr.repository().nameWithOwner()
        );
        triggerReadyLabelDetection(pr.id(), pr.number(), labelName);
    }

    /**
     * Handles PR ready for review events.
     * Schedules detection with 1-hour delay and email notifications enabled.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReady(DomainEvent.PullRequestReady event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        logger.info(
            "PR #{} marked ready for review in {} - scheduling bad practice detection with 1-hour delay",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        scheduleDetectionWithDelay(pr.id(), pr.number());
    }

    /**
     * Handles PR synchronized events (new commits pushed).
     * Reschedules detection with 1-hour delay (cancels previous scheduled task).
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestSynchronized(DomainEvent.PullRequestSynchronized event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        logger.info(
            "PR #{} synchronized in {} - rescheduling bad practice detection with 1-hour delay",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        scheduleDetectionWithDelay(pr.id(), pr.number());
    }

    /**
     * Handles PR reopened events.
     * Schedules detection with 1-hour delay and email notifications enabled.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReopened(DomainEvent.PullRequestReopened event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        logger.info(
            "PR #{} reopened in {} - scheduling bad practice detection with 1-hour delay",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        scheduleDetectionWithDelay(pr.id(), pr.number());
    }

    /**
     * Handles PR closed events.
     * Runs detection immediately without email (for recording purposes).
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestClosed(DomainEvent.PullRequestClosed event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        logger.info(
            "PR #{} closed in {} - triggering immediate bad practice detection (no email)",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        triggerClosedDetection(pr.id(), pr.number());
    }

    /**
     * Schedule detection with 1-hour delay.
     * Used for opened, updated, ready, synchronized, and reopened events.
     */
    private void scheduleDetectionWithDelay(Long pullRequestId, int pullRequestNumber) {
        try {
            PullRequest pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null);
            if (pullRequest == null) {
                logger.warn(
                    "PR #{} (id={}) not found in database, skipping detection",
                    pullRequestNumber,
                    pullRequestId
                );
                return;
            }
            badPracticeDetectorScheduler.detectBadPracticeForPrWhenOpenedOrReadyForReviewEvent(pullRequest);
        } catch (Exception e) {
            logger.error(
                "Error scheduling bad practice detection for PR #{}: {}",
                pullRequestNumber,
                e.getMessage(),
                e
            );
        }
    }

    /**
     * Trigger detection for ready-related labels.
     * Runs immediately (no delay) with email notifications for exact label matches.
     */
    private void triggerReadyLabelDetection(Long pullRequestId, int pullRequestNumber, String labelName) {
        try {
            PullRequest pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null);
            if (pullRequest == null) {
                logger.warn(
                    "PR #{} (id={}) not found in database, skipping detection",
                    pullRequestNumber,
                    pullRequestId
                );
                return;
            }
            // Use the scheduler method that checks exact label names and runs immediately
            badPracticeDetectorScheduler.detectBadPracticeForPrIfReadyLabel(pullRequest, labelName);
        } catch (Exception e) {
            logger.error(
                "Error triggering bad practice detection for PR #{}: {}",
                pullRequestNumber,
                e.getMessage(),
                e
            );
        }
    }

    /**
     * Trigger detection for closed PRs (no email).
     */
    private void triggerClosedDetection(Long pullRequestId, int pullRequestNumber) {
        try {
            PullRequest pullRequest = pullRequestRepository.findById(pullRequestId).orElse(null);
            if (pullRequest == null) {
                logger.warn(
                    "PR #{} (id={}) not found in database, skipping detection",
                    pullRequestNumber,
                    pullRequestId
                );
                return;
            }
            badPracticeDetectorScheduler.detectBadPracticeForPrIfClosedEvent(pullRequest);
        } catch (Exception e) {
            logger.error(
                "Error triggering bad practice detection for closed PR #{}: {}",
                pullRequestNumber,
                e.getMessage(),
                e
            );
        }
    }
}
