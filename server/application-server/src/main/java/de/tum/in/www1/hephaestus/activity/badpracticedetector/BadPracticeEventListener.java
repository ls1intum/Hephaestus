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
 * Event listener that triggers bad practice detection on PR events.
 *
 * <p>Listens for PR created/updated events and asynchronously triggers
 * the bad practice detector to analyze the pull request.
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
            "PR #{} created in {} - triggering bad practice detection",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        detectBadPracticesSafely(pr.id(), pr.number());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestUpdated(DomainEvent.PullRequestUpdated event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        logger.info(
            "PR #{} updated in {} - triggering bad practice detection",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        detectBadPracticesSafely(pr.id(), pr.number());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestLabeled(DomainEvent.PullRequestLabeled event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        String labelName = event.label() != null ? event.label().name() : "";
        if (labelName.toLowerCase().contains("ready") || labelName.toLowerCase().contains("review")) {
            logger.info(
                "PR #{} labeled with '{}' in {} - triggering bad practice detection",
                pr.number(),
                labelName,
                pr.repository().nameWithOwner()
            );
            detectBadPracticesSafely(pr.id(), pr.number());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestReady(DomainEvent.PullRequestReady event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        logger.info(
            "PR #{} marked ready for review in {} - triggering bad practice detection",
            pr.number(),
            pr.repository().nameWithOwner()
        );
        detectBadPracticesSafely(pr.id(), pr.number());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPullRequestSynchronized(DomainEvent.PullRequestSynchronized event) {
        EventPayload.PullRequestData pr = event.pullRequest();
        logger.info(
            "PR #{} synchronized in {} - triggering bad practice detection",
            pr.number(),
            pr.repository().nameWithOwner()
        );
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
            logger.error("Error detecting bad practices for PR #{}: {}", pullRequestNumber, e.getMessage(), e);
        }
    }
}
