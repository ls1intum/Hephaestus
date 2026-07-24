package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.workspace.Workspace;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps each workspace's {@link WorkspaceAgentBinding}s in step with the named-config model it is
 * currently pointed at (#1368). Transitional bridge: while the webapp still edits named
 * {@link AgentConfig}s and the scalar practice/mentor pointers, this mirrors the effective
 * {@code (purpose → model + limits)} into the binding table so the runtime can read the binding as the
 * single source. The following phases make the binding the write target directly and drop the mirror.
 */
@Service
@RequiredArgsConstructor
public class AgentBindingService {

    private final AgentConfigRepository agentConfigRepository;
    private final WorkspaceAgentBindingRepository bindingRepository;

    /**
     * Re-derive the workspace's binding for one purpose from its current scalar pointer. No pointer, a
     * missing/deleted config, or a config without exactly one usable model → the binding is removed
     * (the purpose is unconfigured). Otherwise the binding is upserted from the pointed-at config's
     * model and execution limits. Must run inside the caller's transaction so it sees the just-written
     * pointer and the lazy model associations.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
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
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void syncPurposesBoundTo(Workspace workspace, Long configId) {
        if (configId.equals(workspace.getPracticeConfigId())) {
            sync(workspace, AgentPurpose.PRACTICE_DETECTION);
        }
        if (configId.equals(workspace.getMentorConfigId())) {
            sync(workspace, AgentPurpose.MENTOR);
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
}
