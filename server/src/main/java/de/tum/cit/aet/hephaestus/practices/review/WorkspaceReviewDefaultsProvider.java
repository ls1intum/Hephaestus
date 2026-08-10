package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Looks up a workspace's review defaults by id, for the callers that hold an id rather than the entity.
 *
 * <p>Exists so "how do I get the bottom of the inheritance chain" has one answer. The alternative — every
 * tier-resolving class taking its own {@code WorkspaceRepository} — spread the same two lines across the
 * conversational router, the dormancy report and the artifact trace, and would have spread further with the
 * next consumer.
 *
 * <p>A workspace that has been deleted between the read that produced the id and this one resolves to
 * {@link WorkspaceReviewDefaults#UNSET} rather than throwing: every caller is on a path that is already
 * deciding what to do with findings, and failing a delivery over a race is worse than answering with the
 * defaults the workspace would have had.
 */
@Service
@RequiredArgsConstructor
public class WorkspaceReviewDefaultsProvider {

    private final WorkspaceRepository workspaceRepository;

    @Transactional(readOnly = true)
    public WorkspaceReviewDefaults forWorkspace(Long workspaceId) {
        return WorkspaceReviewDefaults.of(workspaceRepository.findById(workspaceId).orElse(null));
    }

    /**
     * The tier this workspace actually stored, or {@code null} when it has never chosen one.
     *
     * <p>Separate from {@link #forWorkspace} because the two answer different questions and the UI needs
     * both: the effective default is what is in force, this is whether anybody decided it. At the workspace
     * level "inherited" means "we never chose, so the vocabulary's default applies", and that has to read
     * the same way as it does one level down.
     */
    @Transactional(readOnly = true)
    public @Nullable PracticeReviewTier rawDefaultTier(Long workspaceId) {
        return workspaceRepository
            .findById(workspaceId)
            .map(workspace -> workspace.getReviewSettings().getDefaultReviewTier())
            .map(PracticeReviewTier::valueOf)
            .orElse(null);
    }
}
