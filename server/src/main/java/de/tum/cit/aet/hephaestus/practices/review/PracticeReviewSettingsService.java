package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.model.PracticeAutonomy;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes a workspace's practice-review policy overrides, resolving each knob against the
 * fleet default in {@link PracticeReviewProperties}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PracticeReviewSettingsService {

    private final WorkspaceRepository workspaceRepository;
    private final PracticeReviewProperties reviewProperties;
    private final ConfigAuditPort configAudit;

    public PracticeReviewSettingsDTO getSettings(WorkspaceContext workspaceContext) {
        return toView(requireWorkspace(workspaceContext));
    }

    @Transactional
    public PracticeReviewSettingsDTO updatePracticeReview(
        WorkspaceContext workspaceContext,
        UpdatePracticeReviewSettingsRequestDTO req
    ) {
        Workspace workspace = requireWorkspaceForUpdate(workspaceContext);
        PracticeReviewSettings settings = workspace.getReviewSettings();
        PracticeReviewSnapshot before = PracticeReviewSnapshot.of(settings);
        // Reset-to-inherit first, then the value patch, so a field can be reset and re-set in one request.
        settings.reset(req.reset());
        settings.applyPatch(req.deliverToMerged(), req.cooldownMinutes());
        settings.applyScope(req.reviewScope());
        settings.applyDefaultAutonomy(req.defaultAutonomy() == null ? null : req.defaultAutonomy().name());
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.PRACTICE_REVIEW_SETTINGS,
                workspaceContext.id(),
                workspaceContext.id(),
                before,
                PracticeReviewSnapshot.of(settings)
            )
        );
        return toView(workspaceRepository.save(workspace));
    }

    private Workspace requireWorkspace(WorkspaceContext workspaceContext) {
        return workspaceRepository
            .findById(workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));
    }

    /**
     * Locking variant for the audited write: unserialized, two concurrent PATCHes snapshot the same
     * prior state and the audit trail records a transition the second write silently reverts.
     */
    private Workspace requireWorkspaceForUpdate(WorkspaceContext workspaceContext) {
        return workspaceRepository
            .findByIdForUpdate(workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));
    }

    private PracticeReviewSettingsDTO toView(Workspace workspace) {
        PracticeReviewSettings s = workspace.getReviewSettings();
        WorkspaceReviewDefaults defaults = WorkspaceReviewDefaults.of(s);
        return new PracticeReviewSettingsDTO(
            s.resolveDeliverToMerged(reviewProperties.deliverToMerged()),
            s.resolveCooldownMinutes(reviewProperties.cooldownMinutes()),
            s.getDeliverToMerged(),
            s.getCooldownMinutes(),
            s.resolveReviewScope(),
            defaults.defaultAutonomy(),
            s.getDefaultAutonomy() == null ? null : PracticeAutonomy.valueOf(s.getDefaultAutonomy())
        );
    }
}
