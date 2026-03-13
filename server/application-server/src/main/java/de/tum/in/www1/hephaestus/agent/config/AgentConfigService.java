package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.agent.job.AgentJobStatus;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
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
        validateProviderCompatibility(request.agentType(), request.llmProvider());

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
        config.setAgentType(request.agentType());
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
        if (request.timeoutSeconds() != null) {
            config.setTimeoutSeconds(request.timeoutSeconds());
        }
        if (request.maxConcurrentJobs() != null) {
            config.setMaxConcurrentJobs(request.maxConcurrentJobs());
        }
        if (request.allowInternet() != null) {
            config.setAllowInternet(request.allowInternet());
        }

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

        // Validate provider compatibility only when both are being set, or when one is set against the existing value
        AgentType effectiveType = request.agentType() != null ? request.agentType() : config.getAgentType();
        LlmProvider effectiveProvider = request.llmProvider() != null ? request.llmProvider() : config.getLlmProvider();
        validateProviderCompatibility(effectiveType, effectiveProvider);

        if (request.agentType() != null) {
            config.setAgentType(request.agentType());
        }
        if (request.llmProvider() != null) {
            config.setLlmProvider(request.llmProvider());
        }
        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
        if (request.modelName() != null) {
            config.setModelName(request.modelName());
        }
        if (request.llmApiKey() != null) {
            config.setLlmApiKey(request.llmApiKey());
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

        agentConfigRepository.delete(config);
    }

    private void validateProviderCompatibility(AgentType agentType, LlmProvider provider) {
        switch (agentType) {
            case CLAUDE_CODE -> {
                if (provider != LlmProvider.ANTHROPIC) {
                    throw new IllegalArgumentException(
                        "CLAUDE_CODE agent requires ANTHROPIC provider, got: " + provider
                    );
                }
            }
            case CODEX -> {
                if (provider != LlmProvider.OPENAI) {
                    throw new IllegalArgumentException("CODEX agent requires OPENAI provider, got: " + provider);
                }
            }
            case OPENCODE -> {
                /* any provider is valid */
            }
        }
    }
}
