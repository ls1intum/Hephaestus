package de.tum.cit.aet.hephaestus.agent.settings;

import de.tum.cit.aet.hephaestus.agent.catalog.InstanceLlmSettingsService;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfig;
import de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
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

    /**
     * Audit entity ids for the two bindings. There is one of each per workspace, so the binding's own
     * name is its stable key — the workspace is already a column.
     */
    private static final String PRACTICE_CONFIG_BINDING = "practice-config";
    private static final String MENTOR_CONFIG_BINDING = "mentor-config";

    private final WorkspaceRepository workspaceRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final PracticeReviewProperties reviewProperties;
    private final ConfigAuditPort configAudit;
    private final InstanceLlmSettingsService instanceLlmSettingsService;
    private final LlmModelResolver llmModelResolver;

    public AiSettingsViewDTO getSettings(WorkspaceContext workspaceContext) {
        return toView(requireWorkspace(workspaceContext));
    }

    @Transactional
    public AiSettingsViewDTO bindPracticeConfig(WorkspaceContext workspaceContext, @Nullable Long configId) {
        Workspace workspace = requireWorkspaceForUpdate(workspaceContext);
        AgentBindingSnapshot before = new AgentBindingSnapshot(workspace.getPracticeConfigId());
        workspace.setPracticeConfigId(validateExecutableConfigId(workspaceContext.id(), configId));
        auditBinding(workspaceContext, PRACTICE_CONFIG_BINDING, before, workspace.getPracticeConfigId());
        return toView(workspaceRepository.save(workspace));
    }

    @Transactional
    public AiSettingsViewDTO bindMentorConfig(WorkspaceContext workspaceContext, @Nullable Long configId) {
        Workspace workspace = requireWorkspaceForUpdate(workspaceContext);
        AgentBindingSnapshot before = new AgentBindingSnapshot(workspace.getMentorConfigId());
        workspace.setMentorConfigId(validateExecutableConfigId(workspaceContext.id(), configId));
        auditBinding(workspaceContext, MENTOR_CONFIG_BINDING, before, workspace.getMentorConfigId());
        return toView(workspaceRepository.save(workspace));
    }

    @Transactional
    public AiSettingsViewDTO updatePracticeReview(
        WorkspaceContext workspaceContext,
        UpdatePracticeReviewSettingsDTO req
    ) {
        Workspace workspace = requireWorkspaceForUpdate(workspaceContext);
        PracticeReviewSettings settings = workspace.getReviewSettings();
        PracticeReviewSnapshot before = PracticeReviewSnapshot.of(settings);
        // Reset-to-inherit first, then the value patch, so a field can be reset and re-set in one request.
        settings.reset(req.reset());
        settings.applyPatch(req.runForAllUsers(), req.skipDrafts(), req.deliverToMerged(), req.cooldownMinutes());
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

    private void auditBinding(
        WorkspaceContext workspaceContext,
        String bindingId,
        AgentBindingSnapshot before,
        @Nullable Long boundConfigId
    ) {
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.AI_CONFIG_BINDING,
                bindingId,
                workspaceContext.id(),
                before,
                new AgentBindingSnapshot(boundConfigId)
            )
        );
    }

    /** Practice detection and mentor bindings must name an enabled config whose model is usable now. */
    private @Nullable Long validateExecutableConfigId(Long workspaceId, @Nullable Long configId) {
        if (configId == null) {
            return null;
        }
        AgentConfig config = agentConfigRepository
            .findByIdAndWorkspaceId(configId, workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("AgentConfig", configId.toString()));
        if (!config.isEnabled()) {
            throw agentConfigurationUnavailable();
        }
        try {
            if (llmModelResolver.resolve(config) == null) {
                throw agentConfigurationUnavailable();
            }
        } catch (IllegalStateException ignored) {
            throw agentConfigurationUnavailable();
        }
        return configId;
    }

    private static AgentConfigurationUnavailableException agentConfigurationUnavailable() {
        return new AgentConfigurationUnavailableException();
    }

    private Workspace requireWorkspace(WorkspaceContext workspaceContext) {
        return workspaceRepository
            .findById(workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));
    }

    /**
     * Locking variant for the audited writes: the before-snapshot and the mutation must be serialized,
     * or two concurrent PATCHes both snapshot the same prior state and the audit trail records a
     * transition the second write silently reverts.
     */
    private Workspace requireWorkspaceForUpdate(WorkspaceContext workspaceContext) {
        return workspaceRepository
            .findByIdForUpdate(workspaceContext.id())
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
            f.getMentorEnabled(),
            instanceLlmSettingsService.get().isAllowWorkspaceConnections()
        );
    }
}
