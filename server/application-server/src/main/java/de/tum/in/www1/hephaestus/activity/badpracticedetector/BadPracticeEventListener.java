package de.tum.in.www1.hephaestus.activity.badpracticedetector;

import de.tum.in.www1.hephaestus.gitprovider.common.events.EntityEvents;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Listens for pull request events and triggers bad practice detection.
 * <p>
 * This listener is in the activity package because bad practice detection
 * is an application-level feature that reacts to gitprovider domain events.
 * This keeps the gitprovider module focused on data sync without knowledge
 * of consuming features.
 */
@Component
public class BadPracticeEventListener {

    private static final Logger logger = LoggerFactory.getLogger(BadPracticeEventListener.class);

    private final PullRequestBadPracticeDetector badPracticeDetector;

    public BadPracticeEventListener(PullRequestBadPracticeDetector badPracticeDetector) {
        this.badPracticeDetector = badPracticeDetector;
    }

    @Async
    @EventListener
    public void onPullRequestCreated(EntityEvents.Created<?> event) {
        if (event.entity() instanceof PullRequest pr) {
            logger.info(
                "PR #{} created in {} - triggering initial bad practice detection",
                pr.getNumber(),
                pr.getRepository() != null ? pr.getRepository().getNameWithOwner() : "unknown"
            );
            detectBadPracticesSafely(pr);
        }
    }

    @Async
    @EventListener
    public void onPullRequestUpdated(EntityEvents.Updated<?> event) {
        if (event.entity() instanceof PullRequest pr) {
            boolean significantChange =
                event.changedFields().contains("title") ||
                event.changedFields().contains("body") ||
                event.changedFields().contains("labels");

            if (significantChange) {
                logger.info(
                    "PR #{} had significant update ({}) - re-running bad practice detection",
                    pr.getNumber(),
                    event.changedFields()
                );
                detectBadPracticesSafely(pr);
            }
        }
    }

    @Async
    @EventListener
    public void onPullRequestLabeled(EntityEvents.Labeled<?> event) {
        if (event.entity() instanceof PullRequest pr) {
            String labelName = event.label().getName().toLowerCase();
            if (labelName.contains("ready") || labelName.contains("review")) {
                logger.debug(
                    "PR #{} labeled with '{}' - re-running bad practice detection",
                    pr.getNumber(),
                    event.label().getName()
                );
                detectBadPracticesSafely(pr);
            }
        }
    }

    @Async
    @EventListener
    public void onPullRequestReady(EntityEvents.PullRequestReady event) {
        logger.info(
            "PR #{} is ready for review - ensuring bad practices are up to date",
            event.pullRequest().getNumber()
        );
        detectBadPracticesSafely(event.pullRequest());
    }

    @Async
    @EventListener
    public void onPullRequestSynchronized(EntityEvents.PullRequestSynchronized event) {
        logger.debug(
            "PR #{} synchronized (new commits) - re-running bad practice detection",
            event.pullRequest().getNumber()
        );
        detectBadPracticesSafely(event.pullRequest());
    }

    private void detectBadPracticesSafely(PullRequest pr) {
        try {
            badPracticeDetector.detectAndSyncBadPractices(pr);
        } catch (Exception e) {
            logger.error("Error detecting bad practices for PR #{}: {}", pr.getNumber(), e.getMessage());
        }
    }
}
