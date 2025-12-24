package de.tum.in.www1.hephaestus.gitprovider.common.events;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Logs entity lifecycle events for debugging and audit trails.
 */
@Component
public class EntityEventListener {

    private static final Logger logger = LoggerFactory.getLogger(EntityEventListener.class);

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
            logger.debug(
                "PR created: #{} in {} (source: {})",
                pr.getNumber(),
                pr.getRepository() != null ? pr.getRepository().getNameWithOwner() : "unknown",
                event.context().source()
            );
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
            if (!event.changedFields().isEmpty()) {
                logger.debug(
                    "PR #{} updated: {} ({})",
                    pr.getNumber(),
                    event.changedFields(),
                    event.context().source()
                );
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
    public void onPullRequestMerged(EntityEvents.PullRequestMerged event) {
        logger.info("PR #{} merged ({})", event.pullRequest().getNumber(), event.context().source());
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
}
