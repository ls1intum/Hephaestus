package de.tum.cit.aet.hephaestus.agent.settings;

import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.practices.review.PracticeReviewProperties;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceFeatures;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import de.tum.cit.aet.hephaestus.workspace.settings.PracticeReviewSettings;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes the per-workspace AI configuration: which agent config powers practice
 * detection / the mentor, and the practice-review policy overrides. Hosted in the {@code agent}
 * module to avoid a {@code workspace → agent} Modulith cycle; see {@code Workspace.practiceConfigId}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiSettingsService {

    private final WorkspaceRepository workspaceRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final PracticeReviewProperties reviewProperties;

    public AiSettingsViewDTO getSettings(WorkspaceContext workspaceContext) {
        return toView(requireWorkspace(workspaceContext));
    }

    @Transactional
    public AiSettingsViewDTO bindPracticeConfig(WorkspaceContext workspaceContext, @Nullable Long configId) {
        Workspace workspace = requireWorkspace(workspaceContext);
        workspace.setPracticeConfigId(validateConfigId(workspaceContext.id(), configId));
        return toView(workspaceRepository.save(workspace));
    }

    @Transactional
    public AiSettingsViewDTO bindMentorConfig(WorkspaceContext workspaceContext, @Nullable Long configId) {
        Workspace workspace = requireWorkspace(workspaceContext);
        workspace.setMentorConfigId(validateConfigId(workspaceContext.id(), configId));
        return toView(workspaceRepository.save(workspace));
    }

    @Transactional
    public AiSettingsViewDTO updatePracticeReview(
        WorkspaceContext workspaceContext,
        UpdatePracticeReviewSettingsDTO req
    ) {
        Workspace workspace = requireWorkspace(workspaceContext);
        PracticeReviewSettings settings = workspace.getReviewSettings();
        // Reset-to-inherit first, then the value patch, so a field can be reset and re-set in one request.
        settings.reset(req.reset());
        settings.applyPatch(req.runForAllUsers(), req.skipDrafts(), req.deliverToMerged(), req.cooldownMinutes());
        return toView(workspaceRepository.save(workspace));
    }

    /**
     * Validate that the config belongs to this workspace (tenancy-safe scoped lookup). Returns the
     * id unchanged when valid, {@code null} to unbind, or throws 404 for a foreign/missing id.
     */
    private @Nullable Long validateConfigId(Long workspaceId, @Nullable Long configId) {
        if (configId == null) {
            return null;
        }
        agentConfigRepository
            .findByIdAndWorkspaceId(configId, workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("AgentConfig", configId.toString()));
        return configId;
    }

    private Workspace requireWorkspace(WorkspaceContext workspaceContext) {
        return workspaceRepository
            .findById(workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));
    }

    private AiSettingsViewDTO toView(Workspace workspace) {
        PracticeReviewSettings s = workspace.getReviewSettings();
        WorkspaceFeatures f = workspace.getFeatures();
        return new AiSettingsViewDTO(
            workspace.getPracticeConfigId(),
            workspace.getMentorConfigId(),
            s.resolveRunForAllUsers(reviewProperties.runForAllUsers()),
            s.resolveSkipDrafts(reviewProperties.skipDrafts()),
            s.resolveDeliverToMerged(reviewProperties.deliverToMerged()),
            s.resolveCooldownMinutes(reviewProperties.cooldownMinutes()),
            s.getRunForAllUsers(),
            s.getSkipDrafts(),
            s.getDeliverToMerged(),
            s.getCooldownMinutes(),
            f.getPracticesEnabled(),
            f.getMentorEnabled()
        );
    }
}
