package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.job.AgentJobRepository;
import de.tum.in.www1.hephaestus.agent.job.AgentJobStatus;
import de.tum.in.www1.hephaestus.agent.runner.AgentRunner;
import de.tum.in.www1.hephaestus.agent.runner.AgentRunnerHasLinkedConfigsException;
import de.tum.in.www1.hephaestus.agent.runner.AgentRunnerNameConflictException;
import de.tum.in.www1.hephaestus.agent.runner.AgentRunnerRepository;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentConfigService {

    private final AgentConfigRepository agentConfigRepository;
    private final AgentJobRepository agentJobRepository;
    private final AgentRunnerRepository agentRunnerRepository;
    private final WorkspaceRepository workspaceRepository;

    public AgentConfigService(
        AgentConfigRepository agentConfigRepository,
        AgentJobRepository agentJobRepository,
        AgentRunnerRepository agentRunnerRepository,
        WorkspaceRepository workspaceRepository
    ) {
        this.agentConfigRepository = agentConfigRepository;
        this.agentJobRepository = agentJobRepository;
        this.agentRunnerRepository = agentRunnerRepository;
        this.workspaceRepository = workspaceRepository;
    }

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
        String normalizedName = normalizeRequiredName(request.name());

        Long workspaceId = workspaceContext.id();
        if (agentConfigRepository.existsByWorkspaceIdAndName(workspaceId, normalizedName)) {
            throw new AgentConfigNameConflictException(
                "An agent config with name '" + normalizedName + "' already exists in this workspace."
            );
        }

        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));
        AgentRunner runner = resolveRunnerForCreate(workspaceContext, workspace, normalizedName, request);

        AgentConfig config = new AgentConfig();
        config.setWorkspace(workspace);
        config.setName(normalizedName);
        config.setRunner(runner);

        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
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

        if (request.name() != null) {
            String normalizedName = normalizeRequiredName(request.name());
            if (
                !normalizedName.equals(config.getName()) &&
                agentConfigRepository.existsByWorkspaceIdAndName(workspaceContext.id(), normalizedName)
            ) {
                throw new AgentConfigNameConflictException(
                    "An agent config with name '" + normalizedName + "' already exists in this workspace."
                );
            }
            config.setName(normalizedName);
        }

        if (request.enabled() != null) {
            config.setEnabled(request.enabled());
        }

        AgentRunner runner = resolveRunnerForUpdate(workspaceContext, config, request);
        config.setRunner(runner);

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

    private String normalizeRequiredName(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Agent config name is required");
        }
        return normalized;
    }

    private AgentRunner resolveRunnerForCreate(
        WorkspaceContext workspaceContext,
        Workspace workspace,
        String configName,
        CreateAgentConfigRequestDTO request
    ) {
        if (request.runnerId() != null) {
            return agentRunnerRepository
                .findByIdAndWorkspaceId(request.runnerId(), workspaceContext.id())
                .orElseThrow(() -> new EntityNotFoundException("AgentRunner", request.runnerId().toString()));
        }

        AgentRunner runner = new AgentRunner();
        runner.setWorkspace(workspace);
        runner.setName(configName);
        applyRunnerFields(
            runner,
            request.agentType(),
            request.modelName(),
            false,
            request.modelVersion(),
            false,
            request.llmApiKey(),
            false,
            request.llmProvider(),
            request.timeoutSeconds(),
            request.maxConcurrentJobs(),
            request.allowInternet(),
            request.credentialMode()
        );

        if (agentRunnerRepository.existsByWorkspaceIdAndName(workspaceContext.id(), runner.getName())) {
            throw new AgentRunnerNameConflictException(
                "A runner with name '" +
                    runner.getName() +
                    "' already exists in this workspace. Choose that runner explicitly or rename the config."
            );
        }

        validateRunner(runner);
        return agentRunnerRepository.save(runner);
    }

    private AgentRunner resolveRunnerForUpdate(
        WorkspaceContext workspaceContext,
        AgentConfig config,
        UpdateAgentConfigRequestDTO request
    ) {
        AgentRunner currentRunner = config.getRunner();
        AgentRunner targetRunner = currentRunner;

        if (request.runnerId() != null) {
            targetRunner = agentRunnerRepository
                .findByIdAndWorkspaceId(request.runnerId(), workspaceContext.id())
                .orElseThrow(() -> new EntityNotFoundException("AgentRunner", request.runnerId().toString()));
        }

        if (targetRunner == null) {
            throw new EntityNotFoundException("AgentRunner", "null");
        }

        if (!hasInlineRunnerUpdate(request)) {
            return targetRunner;
        }

        if (request.runnerId() != null && currentRunner != null && !currentRunner.getId().equals(request.runnerId())) {
            throw new AgentRunnerHasLinkedConfigsException(
                "Cannot update runner settings while also switching runners in the same request. Reassign first, then edit the runner separately."
            );
        }

        applyRunnerFields(
            targetRunner,
            request.agentType(),
            request.modelName(),
            Boolean.TRUE.equals(request.clearModelName()),
            request.modelVersion(),
            Boolean.TRUE.equals(request.clearModelVersion()),
            request.llmApiKey(),
            Boolean.TRUE.equals(request.clearLlmApiKey()),
            request.llmProvider(),
            request.timeoutSeconds(),
            request.maxConcurrentJobs(),
            request.allowInternet(),
            request.credentialMode()
        );
        validateRunner(targetRunner);
        return agentRunnerRepository.save(targetRunner);
    }

    private boolean hasInlineRunnerUpdate(UpdateAgentConfigRequestDTO request) {
        return (
            request.agentType() != null ||
            request.modelName() != null ||
            Boolean.TRUE.equals(request.clearModelName()) ||
            request.modelVersion() != null ||
            Boolean.TRUE.equals(request.clearModelVersion()) ||
            request.llmApiKey() != null ||
            Boolean.TRUE.equals(request.clearLlmApiKey()) ||
            request.llmProvider() != null ||
            request.timeoutSeconds() != null ||
            request.maxConcurrentJobs() != null ||
            request.allowInternet() != null ||
            request.credentialMode() != null
        );
    }

    private void applyRunnerFields(
        AgentRunner runner,
        AgentType agentType,
        String modelName,
        boolean clearModelName,
        String modelVersion,
        boolean clearModelVersion,
        String llmApiKey,
        boolean clearLlmApiKey,
        LlmProvider llmProvider,
        Integer timeoutSeconds,
        Integer maxConcurrentJobs,
        Boolean allowInternet,
        CredentialMode credentialMode
    ) {
        if (agentType != null) {
            runner.setAgentType(agentType);
        }
        if (llmProvider != null) {
            runner.setLlmProvider(llmProvider);
        }
        if (clearModelName) {
            runner.setModelName(null);
        } else if (modelName != null) {
            runner.setModelName(normalizeOptional(modelName));
        }
        if (clearModelVersion) {
            runner.setModelVersion(null);
        } else if (modelVersion != null) {
            runner.setModelVersion(normalizeOptional(modelVersion));
        }
        if (clearLlmApiKey) {
            runner.setLlmApiKey(null);
        } else if (llmApiKey != null) {
            runner.setLlmApiKey(normalizeOptional(llmApiKey));
        }
        if (timeoutSeconds != null) {
            runner.setTimeoutSeconds(timeoutSeconds);
        }
        if (maxConcurrentJobs != null) {
            runner.setMaxConcurrentJobs(maxConcurrentJobs);
        }
        if (allowInternet != null) {
            runner.setAllowInternet(allowInternet);
        }
        if (credentialMode != null) {
            runner.setCredentialMode(credentialMode);
        }
    }

    private void validateRunner(AgentRunner runner) {
        validateProviderCompatibility(runner.getAgentType(), runner.getLlmProvider());

        if (runner.getAgentType() == AgentType.PI && runner.getCredentialMode() == CredentialMode.OAUTH) {
            throw new AgentConfigCredentialModeException(
                "Pi does not support OAuth credentials. Use Proxy or API key instead."
            );
        }
        if (runner.getCredentialMode() != CredentialMode.PROXY && !runner.isAllowInternet()) {
            throw new AgentConfigCredentialModeException(runner.getCredentialMode());
        }
        if (runner.getCredentialMode() != CredentialMode.PROXY && normalizeOptional(runner.getLlmApiKey()) == null) {
            throw new AgentConfigMissingCredentialException(runner.getCredentialMode());
        }
    }

    private void validateProviderCompatibility(AgentType agentType, LlmProvider provider) {
        if (agentType == null || provider == null) {
            throw new IllegalArgumentException("Agent type and LLM provider are required");
        }
        switch (agentType) {
            case CLAUDE_CODE -> {
                if (provider != LlmProvider.ANTHROPIC) {
                    throw new AgentConfigProviderMismatchException(agentType, LlmProvider.ANTHROPIC, provider);
                }
            }
            case PI -> {
            }
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
