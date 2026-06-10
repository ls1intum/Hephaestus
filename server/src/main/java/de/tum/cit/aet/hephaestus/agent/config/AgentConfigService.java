package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobRepository;
import de.tum.cit.aet.hephaestus.agent.job.AgentJobStatus;
import de.tum.cit.aet.hephaestus.core.exception.EntityNotFoundException;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AgentConfigService {

    private final AgentConfigRepository agentConfigRepository;
    private final AgentJobRepository agentJobRepository;
    private final WorkspaceRepository workspaceRepository;

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
        config.setLlmProvider(request.llmProvider());

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
        if (request.credentialMode() != null) {
            config.setCredentialMode(request.credentialMode());
        }

        validateCredentialMode(config);

        return agentConfigRepository.save(config);
    }

    @Transactional
    public AgentConfig updateConfig(
        WorkspaceContext workspaceContext,
        Long configId,
        UpdateAgentConfigRequestDTO request
    ) {
        AgentConfig config = agentConfigRepository
            .findByIdAndWorkspaceId(configId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("AgentConfig", configId.toString()));

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
        if (request.credentialMode() != null) {
            config.setCredentialMode(request.credentialMode());
        }

        validateCredentialMode(config);

        return agentConfigRepository.save(config);
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

        agentConfigRepository.delete(config);
    }

    /**
     * Validates the direct credential mode (API_KEY): it requires internet access AND a stored
     * credential, since the container reaches the provider directly. Runs on both create and update;
     * on update the merged config still carries the existing key, so a kept key passes.
     */
    private void validateCredentialMode(AgentConfig config) {
        if (config.getCredentialMode() == CredentialMode.PROXY) {
            return;
        }
        if (!config.isAllowInternet()) {
            throw AgentConfigCredentialModeException.requiresInternet(config.getCredentialMode());
        }
        // Only an ENABLED runtime must carry a usable key — a disabled one never runs, so a keyless
        // config can still be renamed or left parked (and, importantly, an already-keyless config can be
        // disabled) without being forced to supply a key first.
        if (config.isEnabled() && (config.getLlmApiKey() == null || config.getLlmApiKey().isBlank())) {
            throw AgentConfigCredentialModeException.missingCredential(config.getCredentialMode());
        }
    }
}
