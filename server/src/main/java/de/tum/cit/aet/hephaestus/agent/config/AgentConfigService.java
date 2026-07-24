package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelWorkspaceGrantRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.ModelVisibility;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentConfigService {

    private final AgentConfigRepository agentConfigRepository;
    private final AgentJobRepository agentJobRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ConfigAuditPort configAudit;
    private final LlmModelRepository llmModelRepository;
    private final WorkspaceLlmModelRepository workspaceLlmModelRepository;
    private final LlmModelWorkspaceGrantRepository llmModelWorkspaceGrantRepository;
    private final AgentBindingService agentBindingService;

    @Transactional(readOnly = true)
    public List<AgentConfig> getConfigs(WorkspaceContext workspaceContext) {
        return agentConfigRepository.findByWorkspaceId(workspaceContext.id());
    }

    @Transactional(readOnly = true)
    public AgentConfig getConfig(WorkspaceContext workspaceContext, Long configId) {
        return agentConfigRepository
            .findByIdAndWorkspaceId(configId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("AgentConfig", configId.toString()));
    }

    @Transactional
    public AgentConfig createConfig(WorkspaceContext workspaceContext, CreateAgentConfigRequestDTO request) {
        Long workspaceId = workspaceContext.id();
        if (agentConfigRepository.existsByWorkspaceIdAndName(workspaceId, request.name())) {
            throw new AgentConfigNameConflictException(
                "An agent config with name '" + request.name() + "' already exists in this workspace."
            );
        }

        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));

        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName(request.name());
        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
        if (request.timeoutSeconds() != null) {
            config.setTimeoutSeconds(request.timeoutSeconds());
        }
        if (request.maxConcurrentJobs() != null) {
            config.setMaxConcurrentJobs(request.maxConcurrentJobs());
        }
        if (request.allowInternet() != null) {
            config.setAllowInternet(request.allowInternet());
        }
        applyModelBinding(config, workspaceContext, request.instanceModelId(), request.workspaceModelId());
        requireAvailableBindingWhenEnabled(config, workspaceId);

        AgentConfig saved;
        try {
            saved = agentConfigRepository.save(config);
        } catch (DataIntegrityViolationException e) {
            // The existsByWorkspaceIdAndName fast-path above is racy: two concurrent creates with the
            // same name both pass the check, and the DB unique constraint uk_agent_config_workspace_name
            // backstops the loser. Translate that loser's violation into the same 409 the fast path
            // advertises, instead of leaking a 500.
            //
            // Scoped to the save alone: the audit write below can raise DataIntegrityViolationException
            // too (its own constraints), and reporting that as "name already exists" would send the
            // operator hunting a duplicate that isn't there while the real fault — a broken audit trail —
            // goes unnamed.
            throw new AgentConfigNameConflictException(
                "An agent config with name '" + request.name() + "' already exists in this workspace."
            );
        }
        configAudit.record(
            ConfigAuditEntry.created(
                ConfigAuditEntityType.AGENT_CONFIG,
                saved.getId(),
                workspaceId,
                AgentConfigSnapshot.of(saved)
            )
        );
        return saved;
    }

    @Transactional
    public AgentConfig updateConfig(
        WorkspaceContext workspaceContext,
        Long configId,
        UpdateAgentConfigRequestDTO request
    ) {
        AgentConfig config = agentConfigRepository
            .findByIdAndWorkspaceIdForUpdate(configId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("AgentConfig", configId.toString()));
        AgentConfigSnapshot before = AgentConfigSnapshot.of(config);

        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
        if (request.timeoutSeconds() != null) {
            config.setTimeoutSeconds(request.timeoutSeconds());
        }
        if (request.maxConcurrentJobs() != null) {
            config.setMaxConcurrentJobs(request.maxConcurrentJobs());
        }
        if (request.allowInternet() != null) {
            config.setAllowInternet(request.allowInternet());
        }
        applyModelBinding(config, workspaceContext, request.instanceModelId(), request.workspaceModelId());
        requireAvailableBindingWhenEnabled(config, workspaceContext.id());

        AgentConfig saved = agentConfigRepository.save(config);
        // Mirror the effective model + limits into any binding currently pointed at this config, so a
        // model/limit/enabled edit propagates to the runtime source of truth (#1368).
        agentBindingService.syncPurposesBoundTo(saved.getWorkspace(), saved.getId());
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.AGENT_CONFIG,
                saved.getId(),
                workspaceContext.id(),
                before,
                AgentConfigSnapshot.of(saved)
            )
        );
        return saved;
    }

    private void requireAvailableBindingWhenEnabled(AgentConfig config, Long workspaceId) {
        if (!config.isEnabled()) return;
        if (config.isEnabled() && (config.getInstanceModel() == null) == (config.getWorkspaceModel() == null)) {
            throw new IllegalArgumentException(
                "An enabled agent config must bind exactly one available catalog model."
            );
        }
        LlmModel instanceModel = config.getInstanceModel();
        if (instanceModel != null) {
            boolean visible =
                instanceModel.getVisibility() == ModelVisibility.PUBLIC ||
                llmModelWorkspaceGrantRepository.existsByIdModelIdAndIdWorkspaceId(instanceModel.getId(), workspaceId);
            if (!instanceModel.isEnabled() || !instanceModel.getConnection().isEnabled() || !visible) {
                throw new IllegalArgumentException("This model isn't available to this workspace.");
            }
            return;
        }
        WorkspaceLlmModel workspaceModel = config.getWorkspaceModel();
        if (!workspaceModel.isEnabled() || !workspaceModel.getConnection().isEnabled()) {
            throw new IllegalArgumentException("This model isn't available to this workspace.");
        }
    }

    @Transactional
    public void deleteConfig(WorkspaceContext workspaceContext, Long configId) {
        AgentConfig config = agentConfigRepository
            .findByIdAndWorkspaceId(configId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("AgentConfig", configId.toString()));

        long activeJobs = agentJobRepository.countByConfigIdAndStatusIn(
            config.getId(),
            Set.of(AgentJobStatus.QUEUED, AgentJobStatus.RUNNING)
        );
        if (activeJobs > 0) {
            throw new AgentConfigHasActiveJobsException(
                "Cannot delete agent config with " + activeJobs + " active job(s). Cancel them first."
            );
        }

        // Scope the bound-check to this workspace's own pointers (the config is already proven
        // in-workspace above) rather than a global cross-tenant query.
        Workspace workspace = workspaceRepository
            .findById(workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));
        if (
            config.getId().equals(workspace.getPracticeConfigId()) ||
            config.getId().equals(workspace.getMentorConfigId())
        ) {
            throw new AgentConfigBoundException(
                "Cannot delete agent config bound to practice detection or the mentor. Unbind it first."
            );
        }

        AgentConfigSnapshot before = AgentConfigSnapshot.of(config);
        agentConfigRepository.delete(config);
        configAudit.record(
            ConfigAuditEntry.deleted(ConfigAuditEntityType.AGENT_CONFIG, config.getId(), workspaceContext.id(), before)
        );
    }

    /**
     * Applies a model-binding write (#1368): exactly one of {@code instanceModelId} /
     * {@code workspaceModelId} may be set (both non-null is a 400), and either clears the other side of
     * the pair. Leaving both ids null is a no-op and preserves the current binding.
     *
     * <p>An enabled config is also revalidated after every update. A revoked binding may still be saved
     * when the same update disables the config, but it cannot remain silently enabled.
     */
    private void applyModelBinding(
        AgentConfig config,
        WorkspaceContext workspaceContext,
        @Nullable Long instanceModelId,
        @Nullable Long workspaceModelId
    ) {
        if (instanceModelId != null && workspaceModelId != null) {
            throw new IllegalArgumentException(
                "An agent config can bind to only one model at a time — a shared model or your own " +
                    "provider's model, not both."
            );
        }
        if (instanceModelId != null) {
            LlmModel model = llmModelRepository
                .findById(instanceModelId)
                .orElseThrow(() -> new EntityNotFoundException("LlmModel", instanceModelId));
            boolean visible =
                model.getVisibility() == ModelVisibility.PUBLIC ||
                llmModelWorkspaceGrantRepository.existsByIdModelIdAndIdWorkspaceId(
                    model.getId(),
                    workspaceContext.id()
                );
            if (!model.isEnabled() || !model.getConnection().isEnabled() || !visible) {
                throw new IllegalArgumentException("This model isn't available to this workspace.");
            }
            config.setInstanceModel(model);
            config.setWorkspaceModel(null);
        } else if (workspaceModelId != null) {
            WorkspaceLlmModel model = workspaceLlmModelRepository
                .findByIdAndWorkspaceId(workspaceModelId, workspaceContext.id())
                .orElseThrow(() -> new EntityNotFoundException("WorkspaceLlmModel", workspaceModelId));
            if (!model.isEnabled() || !model.getConnection().isEnabled()) {
                throw new IllegalArgumentException("This model isn't available to this workspace.");
            }
            config.setWorkspaceModel(model);
            config.setInstanceModel(null);
        }
    }
}
