package de.tum.cit.aet.hephaestus.practices.adapter;

import de.tum.cit.aet.hephaestus.practices.PracticeGoalRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter that handles practices cleanup when a workspace is purged.
 *
 * <p>This contributor:
 * <ul>
 *   <li>Deletes practice findings (via native query through practice.workspace_id)</li>
 *   <li>Deletes practice definitions for the workspace</li>
 * </ul>
 *
 */
@Component
public class PracticesWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    private static final Logger log = LoggerFactory.getLogger(PracticesWorkspacePurgeAdapter.class);

    private final PracticeFindingRepository practiceFindingRepository;
    private final PracticeRepository practiceRepository;
    private final PracticeGoalRepository practiceGoalRepository;

    public PracticesWorkspacePurgeAdapter(
        PracticeFindingRepository practiceFindingRepository,
        PracticeRepository practiceRepository,
        PracticeGoalRepository practiceGoalRepository
    ) {
        this.practiceFindingRepository = practiceFindingRepository;
        this.practiceRepository = practiceRepository;
        this.practiceGoalRepository = practiceGoalRepository;
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        // Delete practice findings explicitly (defense-in-depth; CASCADE would also handle this)
        practiceFindingRepository.deleteAllByPracticeWorkspaceId(workspaceId);
        // Delete practice definitions (CASCADE cleans up any remaining findings); this also clears the
        // practice → practice_goal references, so goals can be removed next.
        practiceRepository.deleteAllByWorkspaceId(workspaceId);
        // Delete practice goals (now unreferenced).
        practiceGoalRepository.deleteAllByWorkspaceId(workspaceId);

        log.info("Deleted practices, goals and findings for workspace: workspaceId={}", workspaceId);
    }

    @Override
    public int getOrder() {
        // Run early, before repository monitors are deleted (order 0 is default).
        return -100;
    }
}
