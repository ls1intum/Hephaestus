package de.tum.in.www1.hephaestus.practices.adapter;

import de.tum.in.www1.hephaestus.practices.PracticesPullRequestQueryRepository;
import de.tum.in.www1.hephaestus.practices.detection.BadPracticeDetectorScheduler;
import de.tum.in.www1.hephaestus.workspace.spi.WorkspacePurgeContributor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter that handles bad practice detection cleanup when a workspace is purged.
 *
 * <p>This contributor cancels all scheduled bad practice detection tasks for pull requests
 * belonging to the workspace being purged. This prevents orphaned scheduled tasks from
 * running after the workspace data has been deleted.
 *
 * <p>Note: This only cancels in-memory scheduled tasks. The persisted bad practice
 * detections (PullRequestBadPractice entities) are cleaned up via cascade delete
 * from the PullRequest entities, which are in turn cleaned up when Repository
 * entities are deleted.
 */
@Component
public class PracticesWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    private static final Logger log = LoggerFactory.getLogger(PracticesWorkspacePurgeAdapter.class);

    private final PracticesPullRequestQueryRepository pullRequestQueryRepository;
    private final BadPracticeDetectorScheduler detectorScheduler;

    public PracticesWorkspacePurgeAdapter(
        PracticesPullRequestQueryRepository pullRequestQueryRepository,
        BadPracticeDetectorScheduler detectorScheduler
    ) {
        this.pullRequestQueryRepository = pullRequestQueryRepository;
        this.detectorScheduler = detectorScheduler;
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        List<Long> pullRequestIds = pullRequestQueryRepository.findPullRequestIdsByWorkspaceId(workspaceId);

        if (pullRequestIds.isEmpty()) {
            log.debug("No scheduled tasks to cancel for workspace: workspaceId={}", workspaceId);
            return;
        }

        int cancelledCount = detectorScheduler.cancelScheduledTasksForPullRequests(pullRequestIds);
        log.debug(
            "Cancelled scheduled bad practice detection tasks for workspace: workspaceId={}, prCount={}, cancelledCount={}",
            workspaceId,
            pullRequestIds.size(),
            cancelledCount
        );
    }

    @Override
    public int getOrder() {
        // Run early, before repository monitors are deleted (order 0 is default).
        // The scheduled tasks reference in-memory data that won't be affected by
        // database deletions, but cancelling early ensures no new tasks are scheduled
        // for PRs that are about to be orphaned.
        return -100;
    }
}
