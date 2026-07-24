package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelResolver;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.WorkspaceLlmModelRepository;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntityType;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditEntry;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort;
import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes each workspace's per-purpose {@link WorkspaceAgentBinding}s (#1368) — the single
 * source the runtime resolves a model from.
 *
 * <p>Also mirrors bindings from the legacy named-{@link AgentConfig} pointers ({@link #sync}) so
 * older config edits still reach the runtime during the transition; a direct binding write clears the
 * matching pointer so the two never fight.
 */
@Service
@RequiredArgsConstructor
public class AgentBindingService {

    private final AgentConfigRepository agentConfigRepository;
    private final WorkspaceAgentBindingRepository bindingRepository;
    private final WorkspaceRepository workspaceRepository;
    private final LlmModelRepository llmModelRepository;
    private final WorkspaceLlmModelRepository workspaceLlmModelRepository;
    private final LlmModelResolver llmModelResolver;
    private final ConfigAuditPort configAudit;

    @Transactional(readOnly = true)
    public List<WorkspaceAgentBinding> getBindings(WorkspaceContext workspaceContext) {
        return bindingRepository.findByWorkspaceId(workspaceContext.id());
    }

    /** True when the binding resolves to an available model right now (UI readiness). */
    @Transactional(readOnly = true)
    public boolean isReady(WorkspaceAgentBinding binding) {
        if (!binding.isEnabled()) {
            return false;
        }
        try {
            llmModelResolver.resolve(binding);
            return true;
        } catch (IllegalStateException unavailable) {
            return false;
        }
    }

    /**
     * Create or replace the workspace's binding for one purpose from a direct write: bind exactly one
     * available model and set the execution limits. Clears the legacy scalar pointer for the purpose so
     * a subsequent config edit's {@link #sync} does not overwrite this direct write.
     */
    @Transactional
    public WorkspaceAgentBinding upsertBinding(
        WorkspaceContext workspaceContext,
        AgentPurpose purpose,
        UpdateAgentBindingRequestDTO request
    ) {
        Long workspaceId = workspaceContext.id();
        Workspace workspace = workspaceRepository
            .findByIdForUpdate(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));

        WorkspaceAgentBinding binding = bindingRepository
            .findByWorkspaceIdAndPurpose(workspaceId, purpose)
            .orElseGet(() -> newBinding(workspace, purpose));
        BindingSnapshot before = BindingSnapshot.of(binding);

        applyModel(binding, workspaceId, request.instanceModelId(), request.workspaceModelId());
        if (request.timeoutSeconds() != null) {
            binding.setTimeoutSeconds(request.timeoutSeconds());
        }
        if (request.maxConcurrentJobs() != null) {
            binding.setMaxConcurrentJobs(request.maxConcurrentJobs());
        }
        if (request.allowInternet() != null) {
            binding.setAllowInternet(request.allowInternet());
        }
        if (request.enabled() != null) {
            binding.setEnabled(request.enabled());
        }

        clearPointer(workspace, purpose);
        WorkspaceAgentBinding saved = bindingRepository.save(binding);
        workspaceRepository.save(workspace);
        audit(purpose, workspaceId, before, BindingSnapshot.of(saved));
        return saved;
    }

    /** Remove the workspace's binding for a purpose (detection/mentor off) and clear its legacy pointer. */
    @Transactional
    public void deleteBinding(WorkspaceContext workspaceContext, AgentPurpose purpose) {
        Long workspaceId = workspaceContext.id();
        Workspace workspace = workspaceRepository
            .findByIdForUpdate(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));
        bindingRepository
            .findByWorkspaceIdAndPurpose(workspaceId, purpose)
            .ifPresent(binding -> {
                BindingSnapshot before = BindingSnapshot.of(binding);
                bindingRepository.delete(binding);
                audit(purpose, workspaceId, before, BindingSnapshot.empty());
            });
        clearPointer(workspace, purpose);
        workspaceRepository.save(workspace);
    }

    private void applyModel(
        WorkspaceAgentBinding binding,
        Long workspaceId,
        @Nullable Long instanceModelId,
        @Nullable Long workspaceModelId
    ) {
        if ((instanceModelId == null) == (workspaceModelId == null)) {
            throw new IllegalArgumentException(
                "A binding must reference exactly one model — a shared model or your own provider's model."
            );
        }
        if (instanceModelId != null) {
            LlmModel model = llmModelRepository
                .findById(instanceModelId)
                .orElseThrow(() -> new EntityNotFoundException("LlmModel", instanceModelId));
            binding.setInstanceModel(model);
            binding.setWorkspaceModel(null);
        } else {
            WorkspaceLlmModel model = workspaceLlmModelRepository
                .findByIdAndWorkspaceId(workspaceModelId, workspaceId)
                .orElseThrow(() -> new EntityNotFoundException("WorkspaceLlmModel", workspaceModelId));
            binding.setWorkspaceModel(model);
            binding.setInstanceModel(null);
        }
        // Availability (enabled + connection enabled + supported protocol + visibility/grant) is exactly
        // what the resolver checks; reuse it instead of duplicating the rules.
        try {
            llmModelResolver.resolve(binding);
        } catch (IllegalStateException unavailable) {
            throw new IllegalArgumentException("This model isn't available to this workspace.");
        }
    }

    // ---- transitional config-mirror (unchanged) --------------------------------------------------

    /**
     * Re-derive the workspace's binding for one purpose from its current scalar pointer. No pointer, a
     * missing/deleted config, or a config without exactly one usable model → the binding is removed
     * (the purpose is unconfigured). Otherwise the binding is upserted from the pointed-at config's
     * model and execution limits. Must run inside the caller's transaction so it sees the just-written
     * pointer and the lazy model associations.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void sync(Workspace workspace, AgentPurpose purpose) {
        Long configId = pointerFor(workspace, purpose);
        WorkspaceAgentBinding existing = bindingRepository
            .findByWorkspaceIdAndPurpose(workspace.getId(), purpose)
            .orElse(null);

        AgentConfig config =
            configId == null
                ? null
                : agentConfigRepository.findByIdAndWorkspaceId(configId, workspace.getId()).orElse(null);

        boolean configured = config != null && hasExactlyOneModel(config);
        if (!configured) {
            if (existing != null) {
                bindingRepository.delete(existing);
            }
            return;
        }

        WorkspaceAgentBinding binding = existing != null ? existing : newBinding(workspace, purpose);
        binding.setInstanceModel(config.getInstanceModel());
        binding.setWorkspaceModel(config.getWorkspaceModel());
        binding.setTimeoutSeconds(config.getTimeoutSeconds());
        binding.setMaxConcurrentJobs(config.getMaxConcurrentJobs());
        binding.setAllowInternet(config.isAllowInternet());
        binding.setEnabled(config.isEnabled());
        bindingRepository.save(binding);
    }

    /** Re-sync every purpose currently pointed at {@code configId} (a config's model/limits changed). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void syncPurposesBoundTo(Workspace workspace, Long configId) {
        if (configId.equals(workspace.getPracticeConfigId())) {
            sync(workspace, AgentPurpose.PRACTICE_DETECTION);
        }
        if (configId.equals(workspace.getMentorConfigId())) {
            sync(workspace, AgentPurpose.MENTOR);
        }
    }

    private void audit(AgentPurpose purpose, Long workspaceId, BindingSnapshot before, BindingSnapshot after) {
        configAudit.record(
            ConfigAuditEntry.updated(
                ConfigAuditEntityType.AI_CONFIG_BINDING,
                purpose.name(),
                workspaceId,
                before,
                after
            )
        );
    }

    private static void clearPointer(Workspace workspace, AgentPurpose purpose) {
        if (purpose == AgentPurpose.PRACTICE_DETECTION) {
            workspace.setPracticeConfigId(null);
        } else {
            workspace.setMentorConfigId(null);
        }
    }

    private static Long pointerFor(Workspace workspace, AgentPurpose purpose) {
        return purpose == AgentPurpose.PRACTICE_DETECTION
            ? workspace.getPracticeConfigId()
            : workspace.getMentorConfigId();
    }

    private static boolean hasExactlyOneModel(AgentConfig config) {
        return (config.getInstanceModel() == null) != (config.getWorkspaceModel() == null);
    }

    private static WorkspaceAgentBinding newBinding(Workspace workspace, AgentPurpose purpose) {
        WorkspaceAgentBinding binding = new WorkspaceAgentBinding();
        binding.setWorkspace(workspace);
        binding.setPurpose(purpose);
        return binding;
    }

    /** Audit projection of a binding's effective model + enabled state. */
    private record BindingSnapshot(
        @Nullable Long instanceModelId,
        @Nullable Long workspaceModelId,
        @Nullable Boolean enabled
    ) implements ConfigAuditSnapshot {
        static BindingSnapshot of(WorkspaceAgentBinding b) {
            return new BindingSnapshot(
                b.getInstanceModel() == null ? null : b.getInstanceModel().getId(),
                b.getWorkspaceModel() == null ? null : b.getWorkspaceModel().getId(),
                b.isEnabled()
            );
        }

        static BindingSnapshot empty() {
            return new BindingSnapshot(null, null, null);
        }
    }
}
