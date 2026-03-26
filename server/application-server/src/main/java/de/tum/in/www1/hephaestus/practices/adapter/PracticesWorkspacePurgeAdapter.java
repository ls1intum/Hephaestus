package de.tum.in.www1.hephaestus.practices.adapter;

import de.tum.in.www1.hephaestus.practices.PracticeRepository;
import de.tum.in.www1.hephaestus.practices.finding.PracticeFindingRepository;
import de.tum.in.www1.hephaestus.workspace.spi.WorkspacePurgeContributor;
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
 */
@Component
public class PracticesWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    private static final Logger log = LoggerFactory.getLogger(PracticesWorkspacePurgeAdapter.class);

    private final PracticeFindingRepository practiceFindingRepository;
    private final PracticeRepository practiceRepository;

    public PracticesWorkspacePurgeAdapter(
        PracticeFindingRepository practiceFindingRepository,
        PracticeRepository practiceRepository
    ) {
        this.practiceFindingRepository = practiceFindingRepository;
        this.practiceRepository = practiceRepository;
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        // Delete practice findings explicitly (defense-in-depth; CASCADE would also handle this)
        practiceFindingRepository.deleteAllByPracticeWorkspaceId(workspaceId);
        // Delete practice definitions (CASCADE cleans up any remaining findings)
        practiceRepository.deleteAllByWorkspaceId(workspaceId);

        log.info("Deleted practices and findings for workspace: workspaceId={}", workspaceId);
    }

    @Override
    public int getOrder() {
        // Run early so findings and practices are removed before repository monitors (order 0).
        return -100;
    }
}
