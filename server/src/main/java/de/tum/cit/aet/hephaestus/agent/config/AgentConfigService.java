package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.LlmProvider;
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
        // #1368: llmProvider is only required for a legacy (unbound) config — a config that binds to
        // a catalog model (instanceModelId/workspaceModelId) never reads it (see LlmModelResolver's
        // precedence). Reject only when NEITHER a binding nor the legacy field is supplied; the
        // `agent_config.llm_provider` column stays NOT NULL (deprecate-then-remove, no schema change
        // here), so a bound-only create still needs a harmless placeholder value on the entity.
        boolean hasModelBinding = request.instanceModelId() != null || request.workspaceModelId() != null;
        if (request.llmProvider() == null && !hasModelBinding) {
            throw new IllegalArgumentException(
                "llmProvider is required unless a model binding (instanceModelId or workspaceModelId) is supplied."
            );
        }
        config.setLlmProvider(request.llmProvider() != null ? request.llmProvider() : LlmProvider.OPENAI);

        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
        if (request.modelName() != null) {
            config.setModelName(request.modelName());
        }
        if (request.llmApiKey() != null) {
            config.setLlmApiKey(request.llmApiKey());
        }
        if (request.llmBaseUrl() != null) {
            // Empty string clears the field; otherwise stores the trimmed value. The request DTO caps
            // this at @Size(max=512), deliberately well under the entity column (length=2048) so trimming
            // after validation can never overflow the column. The wider column is intentional headroom —
            // narrowing it to 512 requires a changelog.
            config.setLlmBaseUrl(request.llmBaseUrl().isBlank() ? null : request.llmBaseUrl().trim());
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
        applyModelBinding(config, workspaceContext, request.instanceModelId(), request.workspaceModelId(), null);

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

        if (request.llmProvider() != null) {
            config.setLlmProvider(request.llmProvider());
        }
        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
        if (request.modelName() != null) {
            config.setModelName(request.modelName());
        }
        // Clearing the key wins over a provided value, so an accidental "clear + new key" still clears.
        if (Boolean.TRUE.equals(request.clearLlmApiKey())) {
            config.setLlmApiKey(null);
        } else if (request.llmApiKey() != null) {
            config.setLlmApiKey(request.llmApiKey());
        }
        if (request.llmBaseUrl() != null) {
            // Empty string clears the field; otherwise stores the trimmed value.
            config.setLlmBaseUrl(request.llmBaseUrl().isBlank() ? null : request.llmBaseUrl().trim());
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
        applyModelBinding(
            config,
            workspaceContext,
            request.instanceModelId(),
            request.workspaceModelId(),
            request.clearModelBinding()
        );

        AgentConfig saved = agentConfigRepository.save(config);
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
     * the pair. {@code clearModelBinding=true} resets both to {@code null}, reverting to the legacy
     * provider fields. Leaving both ids null (and no clear flag) is a no-op — it preserves the config's
     * current binding, matching this DTO's partial-update convention elsewhere; the pre-existing
     * {@code (NULL, NULL)} state of an unmigrated config is therefore never touched by an unrelated PATCH.
     *
     * <p>Bind-time validation only runs on the id actually supplied in this call, never on whatever the
     * config already carries — re-validating an untouched binding on every unrelated field update would
     * turn a later model/connection disable into a landmine for every config that happens to reference it.
     */
    private void applyModelBinding(
        AgentConfig config,
        WorkspaceContext workspaceContext,
        @Nullable Long instanceModelId,
        @Nullable Long workspaceModelId,
        @Nullable Boolean clearModelBinding
    ) {
        if (Boolean.TRUE.equals(clearModelBinding)) {
            config.setInstanceModel(null);
            config.setWorkspaceModel(null);
            return;
        }
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
