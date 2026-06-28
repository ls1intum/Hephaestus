package de.tum.cit.aet.hephaestus.practices.adapter;

import de.tum.cit.aet.hephaestus.practices.PracticeAreaRepository;
import de.tum.cit.aet.hephaestus.practices.PracticeRepository;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import de.tum.cit.aet.hephaestus.workspace.spi.WorkspacePurgeContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Adapter that handles practices cleanup when a workspace is purged.
 *
 * <p>This contributor deletes, in dependency order:
 * <ul>
 *   <li>Feedback (CASCADE cleans feedback_observation/placement/reaction)</li>
 *   <li>Observations (via native query through practice.workspace_id)</li>
 *   <li>Practice definitions for the workspace</li>
 *   <li>Practice areas (now unreferenced)</li>
 * </ul>
 */
@Component
public class PracticesWorkspacePurgeAdapter implements WorkspacePurgeContributor {

    private static final Logger log = LoggerFactory.getLogger(PracticesWorkspacePurgeAdapter.class);

    private final FeedbackRepository feedbackRepository;
    private final ObservationRepository observationRepository;
    private final PracticeRepository practiceRepository;
    private final PracticeAreaRepository practiceAreaRepository;

    public PracticesWorkspacePurgeAdapter(
        FeedbackRepository feedbackRepository,
        ObservationRepository observationRepository,
        PracticeRepository practiceRepository,
        PracticeAreaRepository practiceAreaRepository
    ) {
        this.feedbackRepository = feedbackRepository;
        this.observationRepository = observationRepository;
        this.practiceRepository = practiceRepository;
        this.practiceAreaRepository = practiceAreaRepository;
    }

    @Override
    public void deleteWorkspaceData(Long workspaceId) {
        // Delete feedback first (CASCADE cleans feedback_observation/placement/reaction). The purge is a
        // soft-delete, so the RESTRICT FK on feedback never fires — these rows must be removed explicitly.
        feedbackRepository.deleteAllByWorkspaceId(workspaceId);
        // Delete observations explicitly (defense-in-depth; CASCADE would also handle this).
        observationRepository.deleteAllByPracticeWorkspaceId(workspaceId);
        // Delete practice definitions (CASCADE cleans up any remaining observations); this also clears the
        // practice → practice_area references, so areas can be removed next.
        practiceRepository.deleteAllByWorkspaceId(workspaceId);
        // Delete practice areas (now unreferenced).
        practiceAreaRepository.deleteAllByWorkspaceId(workspaceId);

        log.info("Deleted feedback, observations, practices and areas for workspace: workspaceId={}", workspaceId);
    }

    @Override
    public int getOrder() {
        // Run early, before repository monitors are deleted (order 0 is default).
        return -100;
    }
}
