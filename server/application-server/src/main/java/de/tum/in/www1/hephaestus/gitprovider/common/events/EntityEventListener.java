package de.tum.in.www1.hephaestus.gitprovider.common.events;

import de.tum.in.www1.hephaestus.activity.badpracticedetector.PullRequestBadPracticeDetector;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Unified event listener for all entity events.
 * <p>
 * This single listener replaces the separate IssueEventListener and
 * PullRequestEventListener classes by handling the generic EntityEvents.
 * <p>
 * <b>Design Benefits:</b>
 * <ul>
 * <li>One file instead of 2+ listener files per entity</li>
 * <li>Generic event handling with instanceof checks for type-specific
 * logic</li>
 * <li>Decoupled from specific event types</li>
 * </ul>
 */
@Component
public class EntityEventListener {

    private static final Logger logger = LoggerFactory.getLogger(EntityEventListener.class);

    private final PullRequestBadPracticeDetector badPracticeDetector;

    public EntityEventListener(PullRequestBadPracticeDetector badPracticeDetector) {
        this.badPracticeDetector = badPracticeDetector;
    }

    @Async
    @EventListener
    public void onEntityCreated(EntityEvents.Created<?> event) {
        Object entity = event.entity();

        if (entity instanceof Issue issue && !issue.isPullRequest()) {
            logger.debug(
                "Issue created: #{} in {} (source: {})",
                issue.getNumber(),
                issue.getRepository() != null ? issue.getRepository().getNameWithOwner() : "unknown",
                event.context().source()
            );
        } else if (entity instanceof PullRequest pr) {
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
    public void onEntityUpdated(EntityEvents.Updated<?> event) {
        Object entity = event.entity();

        if (entity instanceof Issue issue && !issue.isPullRequest()) {
            if (!event.changedFields().isEmpty()) {
                logger.debug(
                    "Issue #{} updated: {} ({})",
                    issue.getNumber(),
                    event.changedFields(),
                    event.context().source()
                );
            }
        } else if (entity instanceof PullRequest pr) {
            // Only re-run detection if significant fields changed
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
    public void onEntityClosed(EntityEvents.Closed<?> event) {
        Object entity = event.entity();

        if (entity instanceof Issue issue && !issue.isPullRequest()) {
            logger.info(
                "Issue closed: #{} reason={} ({})",
                issue.getNumber(),
                event.stateReason(),
                event.context().source()
            );
        } else if (entity instanceof PullRequest pr) {
            logger.debug("PR #{} closed: {} ({})", pr.getNumber(), event.stateReason(), event.context().source());
        }
    }

    @Async
    @EventListener
    public void onEntityLabeled(EntityEvents.Labeled<?> event) {
        Object entity = event.entity();
        String labelName = event.label().getName().toLowerCase();

        if (entity instanceof PullRequest pr) {
            // Check if this is a lifecycle label that might affect detection
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
    public void onPullRequestMerged(EntityEvents.PullRequestMerged event) {
        logger.info("PR #{} merged ({})", event.pullRequest().getNumber(), event.context().source());
        // Future: Update metrics, generate reports, etc.
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

    @Async
    @EventListener
    public void onIssueTyped(EntityEvents.Typed event) {
        logger.info(
            "Issue #{} typed as '{}' ({})",
            event.issue().getNumber(),
            event.issueType().getName(),
            event.context().source()
        );
    }

    // ==================== Helper Methods ====================

    private void detectBadPracticesSafely(PullRequest pr) {
        try {
            badPracticeDetector.detectAndSyncBadPractices(pr);
        } catch (Exception e) {
            logger.error("Error detecting bad practices for PR #{}: {}", pr.getNumber(), e.getMessage());
        }
    }
}
