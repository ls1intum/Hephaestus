package de.tum.in.www1.hephaestus.practices.detection;

import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventContext;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequestRepository;
import java.time.Duration;
import java.time.Instant;
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

    private static final Logger log = LoggerFactory.getLogger(BadPracticeEventListener.class);

    /**
     * Maximum age of a PR for detection during sync events.
     * PRs older than this are skipped during backfill/incremental sync.
     * Allows detection for PRs created during system downtime (catchup scenario).
     */
    private static final Duration MAX_PR_AGE_FOR_SYNC_DETECTION = Duration.ofHours(24);

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
     * Determines if detection should be skipped for the given event.
     *
     * <p>Detection is skipped for sync events (backfill/incremental) when:
     * <ul>
     *   <li>The PR is already closed or merged (no actionable feedback)</li>
     *   <li>The PR was created more than 24 hours ago (stale backfill data)</li>
     * </ul>
     *
     * <p>Webhook events are always processed - they represent real-time activity
     * where detection feedback is most valuable.
     *
     * <p>Recent PRs discovered via sync (created within 24 hours) are still processed
     * to handle the "downtime catchup" scenario where webhooks were missed.
     *
     * @param context the event context containing source information
     * @param pr the pull request data from the event
     * @return true if detection should be skipped, false otherwise
     */
    private boolean shouldSkipDetection(EventContext context, EventPayload.PullRequestData pr) {
        // Webhook events are the primary use case - always process
        if (context.isWebhook()) {
            return false;
        }

        // For sync events, apply filtering to avoid wasted work on stale data
        String repoName = pr.repository().nameWithOwner();

        // Skip if PR is already closed or merged - no point in detection
        if (pr.state() == Issue.State.CLOSED || pr.state() == Issue.State.MERGED || pr.isMerged()) {
            log.debug(
                "Skipped detection for sync event: prNumber={}, repoName={}, reason=alreadyClosed, state={}",
                pr.number(),
                repoName,
                pr.state()
            );
            return true;
        }

        // Skip if PR is too old (backfill scenario)
        // Allow recent PRs for downtime catchup
        Instant createdAt = pr.createdAt();
        if (createdAt != null && createdAt.isBefore(Instant.now().minus(MAX_PR_AGE_FOR_SYNC_DETECTION))) {
            log.debug(
                "Skipped detection for sync event: prNumber={}, repoName={}, reason=prTooOld, createdAt={}",
                pr.number(),
                repoName,
                createdAt
            );
            return true;
        }

        // Allow this sync event - PR is recent and still open
        log.debug(
            "Processing sync event for recent PR: prNumber={}, repoName={}, createdAt={}",
            pr.number(),
            repoName,
            createdAt
        );
        return false;
    }

    /**
     * Handles PR created events.
     * Schedules detection with 1-hour delay and email notifications enabled.
     * Skips sync events for closed/old PRs.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestCreated(DomainEvent.PullRequestCreated event) {
        EventPayload.PullRequestData pr = event.pullRequest();

        if (shouldSkipDetection(event.context(), pr)) {
            return;
        }

        log.info(
            "Scheduling bad practice detection: prNumber={}, repoName={}, delay=1hour, event=created",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        scheduleDetectionWithDelay(pr.id(), pr.number());
    }

    /**
     * Handles PR updated events.
     * Reschedules detection with 1-hour delay (cancels previous scheduled task).
     * Skips sync events for closed/old PRs.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestUpdated(DomainEvent.PullRequestUpdated event) {
        EventPayload.PullRequestData pr = event.pullRequest();

        if (shouldSkipDetection(event.context(), pr)) {
            return;
        }

        log.info(
            "Rescheduling bad practice detection: prNumber={}, repoName={}, delay=1hour, event=updated",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        scheduleDetectionWithDelay(pr.id(), pr.number());
    }

    /**
     * Handles PR labeled events.
     * Only triggers for ready-related labels (exact match), runs immediately with email notifications.
     * Skips sync events for closed/old PRs.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestLabeled(DomainEvent.PullRequestLabeled event) {
        EventPayload.PullRequestData pr = event.pullRequest();

        if (shouldSkipDetection(event.context(), pr)) {
            return;
        }

        String labelName = event.label() != null ? event.label().name() : "";
        log.info(
            "Checking for ready label detection: prNumber={}, labelName={}, repoName={}",
            pr.number(),
            labelName,
            pr.repository().nameWithOwner()
        );
        triggerReadyLabelDetection(pr.id(), pr.number(), labelName);
    }

    /**
     * Handles PR ready for review events.
     * Schedules detection with 1-hour delay and email notifications enabled.
     * Skips sync events for closed/old PRs.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReady(DomainEvent.PullRequestReady event) {
        EventPayload.PullRequestData pr = event.pullRequest();

        if (shouldSkipDetection(event.context(), pr)) {
            return;
        }

        log.info(
            "Scheduling bad practice detection: prNumber={}, repoName={}, delay=1hour, event=readyForReview",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        scheduleDetectionWithDelay(pr.id(), pr.number());
    }

    /**
     * Handles PR synchronized events (new commits pushed).
     * Reschedules detection with 1-hour delay (cancels previous scheduled task).
     * Skips sync events for closed/old PRs.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestSynchronized(DomainEvent.PullRequestSynchronized event) {
        EventPayload.PullRequestData pr = event.pullRequest();

        if (shouldSkipDetection(event.context(), pr)) {
            return;
        }

        log.info(
            "Rescheduling bad practice detection: prNumber={}, repoName={}, delay=1hour, event=synchronized",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        scheduleDetectionWithDelay(pr.id(), pr.number());
    }

    /**
     * Handles PR reopened events.
     * Schedules detection with 1-hour delay and email notifications enabled.
     * Skips sync events for closed/old PRs.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReopened(DomainEvent.PullRequestReopened event) {
        EventPayload.PullRequestData pr = event.pullRequest();

        if (shouldSkipDetection(event.context(), pr)) {
            return;
        }

        log.info(
            "Scheduling bad practice detection: prNumber={}, repoName={}, delay=1hour, event=reopened",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        scheduleDetectionWithDelay(pr.id(), pr.number());
    }

    /**
     * Handles PR closed events.
     * Runs detection immediately without email (for recording purposes).
     * Skips sync events - closed PRs discovered during backfill are too stale for detection.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestClosed(DomainEvent.PullRequestClosed event) {
        EventPayload.PullRequestData pr = event.pullRequest();

        // For closed events, skip ALL sync events - these are historical
        // The shouldSkipDetection check for "already closed" would pass, but
        // we also want to skip closed PRs that are still within 24 hours
        if (event.context().isSync()) {
            log.debug(
                "Skipped closed detection for sync event: prNumber={}, repoName={}, reason=syncEvent",
                pr.number(),
                pr.repository().nameWithOwner()
            );
            return;
        }

        log.info(
            "Triggering immediate bad practice detection: prNumber={}, repoName={}, event=closed, sendEmail=false",
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
            PullRequest pullRequest = pullRequestRepository.findByIdWithAssignees(pullRequestId).orElse(null);
            if (pullRequest == null) {
                log.warn(
                    "Skipped bad practice detection: reason=pullRequestNotFound, prNumber={}, prId={}",
                    pullRequestNumber,
                    pullRequestId
                );
                return;
            }
            badPracticeDetectorScheduler.detectBadPracticeForPrWhenOpenedOrReadyForReviewEvent(pullRequest);
        } catch (Exception e) {
            log.error("Failed to schedule bad practice detection: prNumber={}", pullRequestNumber, e);
        }
    }

    /**
     * Trigger detection for ready-related labels.
     * Runs immediately (no delay) with email notifications for exact label matches.
     */
    private void triggerReadyLabelDetection(Long pullRequestId, int pullRequestNumber, String labelName) {
        try {
            PullRequest pullRequest = pullRequestRepository.findByIdWithAssignees(pullRequestId).orElse(null);
            if (pullRequest == null) {
                log.warn(
                    "Skipped bad practice detection: reason=pullRequestNotFound, prNumber={}, prId={}",
                    pullRequestNumber,
                    pullRequestId
                );
                return;
            }
            // Use the scheduler method that checks exact label names and runs immediately
            badPracticeDetectorScheduler.detectBadPracticeForPrIfReadyLabel(pullRequest, labelName);
        } catch (Exception e) {
            log.error("Failed to trigger bad practice detection: prNumber={}", pullRequestNumber, e);
        }
    }

    /**
     * Trigger detection for closed PRs (no email).
     */
    private void triggerClosedDetection(Long pullRequestId, int pullRequestNumber) {
        try {
            PullRequest pullRequest = pullRequestRepository.findByIdWithAssignees(pullRequestId).orElse(null);
            if (pullRequest == null) {
                log.warn(
                    "Skipped bad practice detection: reason=pullRequestNotFound, prNumber={}, prId={}",
                    pullRequestNumber,
                    pullRequestId
                );
                return;
            }
            badPracticeDetectorScheduler.detectBadPracticeForPrIfClosedEvent(pullRequest);
        } catch (Exception e) {
            log.error("Failed to trigger closed PR detection: prNumber={}", pullRequestNumber, e);
        }
    }
}
