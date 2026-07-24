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
 * older config edits still reach the runtime during the transition; a direct binding write clears the
 * matching pointer so the two never fight.
 */
@Service
@RequiredArgsConstructor
public class AgentBindingService {

    private final WorkspaceAgentBindingRepository bindingRepository;
    private final WorkspaceRepository workspaceRepository;
    private final LlmModelRepository llmModelRepository;
    private final WorkspaceLlmModelRepository workspaceLlmModelRepository;
    private final LlmModelResolver llmModelResolver;
    private final ConfigAuditPort configAudit;

    @Transactional(readOnly = true)
    public List<WorkspaceAgentBinding> getBindings(WorkspaceContext workspaceContext) {
        // Fetches the model + connection graph: the caller reports readiness per binding, which
        // resolves those associations after this transaction has closed (see isReady).
        return bindingRepository.findByWorkspaceIdWithModels(workspaceContext.id());
    }

    /**
     * True when the binding resolves to an available model right now (UI readiness).
     *
     * <p>Deliberately NOT transactional: resolve() reports a revoked model by throwing, and catching
     * that inside a shared transaction would still mark it rollback-only. The binding must therefore
     * arrive with its model graph already fetched — {@link #getBindings} and the write paths do that.
     */
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
        AgentBindingUpsertRequestDTO request
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

        WorkspaceAgentBinding saved = bindingRepository.save(binding);
        audit(purpose, workspaceId, before, BindingSnapshot.of(saved));
        return saved;
    }

    /** Remove the workspace's binding for a purpose (detection/mentor off). */
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
