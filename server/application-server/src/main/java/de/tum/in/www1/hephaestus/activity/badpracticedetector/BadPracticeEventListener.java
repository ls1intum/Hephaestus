package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.gitprovider.common.events.DomainEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.events.EventPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for pull request events and triggers bad practice detection.
 * <p>
 * This listener is in the activity package because bad practice detection
 * is an application-level feature that reacts to gitprovider domain events.
 * This keeps the gitprovider module focused on data sync without knowledge
 * of consuming features.
 * <p>
 * Uses type-safe DomainEvent.PullRequestEvent subtypes - no instanceof checks needed!
 */
@Component
public class BadPracticeEventListener {

    private static final Logger logger = LoggerFactory.getLogger(BadPracticeEventListener.class);

    private final PullRequestBadPracticeDetector badPracticeDetector;

    public BadPracticeEventListener(PullRequestBadPracticeDetector badPracticeDetector) {
        this.badPracticeDetector = badPracticeDetector;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestCreated(DomainEvent.PullRequestCreated event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        logger.info(
            "PR #{} created in {} - triggering initial bad practice detection",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        detectBadPracticesSafely(pr.id(), pr.number());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestUpdated(DomainEvent.PullRequestUpdated event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        boolean significantChange =
            event.changedFields().contains("title") ||
            event.changedFields().contains("body") ||
            event.changedFields().contains("labels");

        if (significantChange) {
            logger.info(
                "PR #{} had significant update ({}) - re-running bad practice detection",
                pr.number(),
                event.changedFields()
            );
            detectBadPracticesSafely(pr.id(), pr.number());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestLabeled(DomainEvent.PullRequestLabeled event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        String labelName = event.label().name().toLowerCase();
        if (labelName.contains("ready") || labelName.contains("review")) {
            logger.debug(
                "PR #{} labeled with '{}' - re-running bad practice detection",
                pr.number(),
                event.label().name()
            );
            detectBadPracticesSafely(pr.id(), pr.number());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReady(DomainEvent.PullRequestReady event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        logger.info("PR #{} is ready for review - ensuring bad practices are up to date", pr.number());
        detectBadPracticesSafely(pr.id(), pr.number());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestSynchronized(DomainEvent.PullRequestSynchronized event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        logger.debug("PR #{} synchronized (new commits) - re-running bad practice detection", pr.number());
        detectBadPracticesSafely(pr.id(), pr.number());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReopened(DomainEvent.PullRequestReopened event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        logger.info(
            "PR #{} reopened in {} - triggering bad practice detection",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        detectBadPracticesSafely(pr.id(), pr.number());
    }

    private void detectBadPracticesSafely(Long pullRequestId, int pullRequestNumber) {
        try {
            badPracticeDetector.detectAndSyncBadPractices(pullRequestId);
        } catch (Exception e) {
            logger.error("Error detecting bad practices for PR #{}: {}", pullRequestNumber, e.getMessage());
        }
    }
}
