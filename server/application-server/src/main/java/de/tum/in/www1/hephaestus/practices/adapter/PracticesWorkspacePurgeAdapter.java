package de.tum.in.www1.hephaestus.practices.adapter;

import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.PracticesPullRequestQueryRepository;
import de.tum.in.www1.hephaestus.practices.detection.BadPracticeDetectorScheduler;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.in.www1.hephaestus.workspace.spi.WorkspacePurgeContributor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter that handles practices cleanup when a workspace is purged.
 *
 * <p>This contributor:
 * <ul>
 *   <li>Cancels all scheduled bad practice detection tasks for pull requests</li>
 *   <li>Deletes practice findings (via native query through practice.workspace_id)</li>
 *   <li>Deletes practice definitions for the workspace</li>
 * </ul>
 *
 * <p>Note: The persisted bad practice detections (PullRequestBadPractice entities) are
 * cleaned up via cascade delete from the PullRequest entities, which are in turn
 * cleaned up when Repository entities are deleted.
 */
@Component
public class PracticesWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    private static final Logger log = LoggerFactory.getLogger(PracticesWorkspacePurgeAdapter.class);

    private final PracticesPullRequestQueryRepository pullRequestQueryRepository;
    private final BadPracticeDetectorScheduler detectorScheduler;
    private final PracticeFindingRepository practiceFindingRepository;
    private final PracticeRepository practiceRepository;

    public PracticesWorkspacePurgeAdapter(
        PracticesPullRequestQueryRepository pullRequestQueryRepository,
        BadPracticeDetectorScheduler detectorScheduler,
        PracticeFindingRepository practiceFindingRepository,
        PracticeRepository practiceRepository
    ) {
        this.pullRequestQueryRepository = pullRequestQueryRepository;
        this.detectorScheduler = detectorScheduler;
        this.practiceFindingRepository = practiceFindingRepository;
        this.practiceRepository = practiceRepository;
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        // Cancel scheduled bad practice detection tasks
        List<Long> pullRequestIds = pullRequestQueryRepository.findPullRequestIdsByWorkspaceId(workspaceId);

        if (!pullRequestIds.isEmpty()) {
            int cancelledCount = detectorScheduler.cancelScheduledTasksForPullRequests(pullRequestIds);
            log.debug(
                "Cancelled scheduled bad practice detection tasks for workspace: workspaceId={}, prCount={}, cancelledCount={}",
                workspaceId,
                pullRequestIds.size(),
                cancelledCount
            );
        }

        // Delete practice findings (must happen before practices due to FK)
        practiceFindingRepository.deleteAllByPracticeWorkspaceId(workspaceId);
        // Delete practice definitions
        practiceRepository.deleteAllByWorkspaceId(workspaceId);

        log.info("Deleted practices and findings for workspace: workspaceId={}", workspaceId);
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
