package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.agent.job.AgentJobStatus;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import java.util.Optional;
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
    public Optional<AgentConfig> getConfig(WorkspaceContext workspaceContext) {
        return agentConfigRepository.findByWorkspaceId(workspaceContext.id());
    }

    @Transactional
    public AgentConfig createOrUpdateConfig(WorkspaceContext workspaceContext, UpdateAgentConfigRequestDTO request) {
        validateProviderCompatibility(request.agentType(), request.llmProvider());

        Long workspaceId = workspaceContext.id();
        AgentConfig config = agentConfigRepository
            .findByWorkspaceId(workspaceId)
            .orElseGet(() -> {
                Workspace workspace = workspaceRepository
                    .findById(workspaceId)
                    .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));
                AgentConfig newConfig = new AgentConfig();
                newConfig.setWorkspace(workspace);
                return newConfig;
            });

        config.setAgentType(request.agentType());
        config.setLlmProvider(request.llmProvider());

        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }
        if (request.modelName() != null) {
            config.setModelName(request.modelName());
        }
        // Only update API key if explicitly provided (null = keep existing)
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
    public void deleteConfig(WorkspaceContext workspaceContext) {
        Long workspaceId = workspaceContext.id();

        AgentConfig config = agentConfigRepository
            .findByWorkspaceId(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("AgentConfig", workspaceContext.slug()));

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
