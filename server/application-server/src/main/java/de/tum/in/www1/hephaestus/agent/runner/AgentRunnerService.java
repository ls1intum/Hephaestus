package de.tum.in.www1.hephaestus.agent.runner;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigCredentialModeException;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigMissingCredentialException;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigProviderMismatchException;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRunnerService {

    private final AgentRunnerRepository agentRunnerRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final WorkspaceRepository workspaceRepository;

    public AgentRunnerService(
        AgentRunnerRepository agentRunnerRepository,
        AgentConfigRepository agentConfigRepository,
        WorkspaceRepository workspaceRepository
    ) {
        this.agentRunnerRepository = agentRunnerRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public List<AgentRunner> getRunners(WorkspaceContext workspaceContext) {
        return agentRunnerRepository.findByWorkspaceId(workspaceContext.id());
    }

    @Transactional(readOnly = true)
    public AgentRunner getRunner(WorkspaceContext workspaceContext, Long runnerId) {
        return agentRunnerRepository
            .findByIdAndWorkspaceId(runnerId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("AgentRunner", runnerId.toString()));
    }

    @Transactional
    public AgentRunner createRunner(WorkspaceContext workspaceContext, CreateAgentRunnerRequestDTO request) {
        String normalizedName = normalizeRequiredName(request.name());
        validateProviderCompatibility(request.agentType(), request.llmProvider());

        Long workspaceId = workspaceContext.id();
        if (agentRunnerRepository.existsByWorkspaceIdAndName(workspaceId, normalizedName)) {
            throw new AgentRunnerNameConflictException(
                "A runner with name '" + normalizedName + "' already exists in this workspace."
            );
        }

        Workspace workspace = workspaceRepository
            .findById(workspaceId)
            .orElseThrow(() -> new EntityNotFoundException("Workspace", workspaceContext.slug()));

        AgentRunner runner = new AgentRunner();
        runner.setWorkspace(workspace);
        runner.setName(normalizedName);
        runner.setAgentType(request.agentType());
        runner.setLlmProvider(request.llmProvider());
        runner.setModelName(normalizeOptional(request.modelName()));
        runner.setModelVersion(normalizeOptional(request.modelVersion()));
        runner.setLlmApiKey(normalizeOptional(request.llmApiKey()));
        if (request.timeoutSeconds() != null) {
            runner.setTimeoutSeconds(request.timeoutSeconds());
        }
        if (request.maxConcurrentJobs() != null) {
            runner.setMaxConcurrentJobs(request.maxConcurrentJobs());
        }
        if (request.allowInternet() != null) {
            runner.setAllowInternet(request.allowInternet());
        }
        if (request.credentialMode() != null) {
            runner.setCredentialMode(request.credentialMode());
        }

        validateCredentialMode(runner);
        return agentRunnerRepository.save(runner);
    }

    @Transactional
    public AgentRunner updateRunner(
        WorkspaceContext workspaceContext,
        Long runnerId,
        UpdateAgentRunnerRequestDTO request
    ) {
        AgentRunner runner = agentRunnerRepository
            .findByIdAndWorkspaceId(runnerId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("AgentRunner", runnerId.toString()));

        AgentType effectiveType = request.agentType() != null ? request.agentType() : runner.getAgentType();
        LlmProvider effectiveProvider = request.llmProvider() != null ? request.llmProvider() : runner.getLlmProvider();
        validateProviderCompatibility(effectiveType, effectiveProvider);

        if (request.name() != null) {
            String normalizedName = normalizeRequiredName(request.name());
            if (
                !normalizedName.equals(runner.getName()) &&
                agentRunnerRepository.existsByWorkspaceIdAndName(workspaceContext.id(), normalizedName)
            ) {
                throw new AgentRunnerNameConflictException(
                    "A runner with name '" + normalizedName + "' already exists in this workspace."
                );
            }
            runner.setName(normalizedName);
        }

        if (request.agentType() != null) {
            runner.setAgentType(request.agentType());
        }
        if (request.llmProvider() != null) {
            runner.setLlmProvider(request.llmProvider());
        }
        if (Boolean.TRUE.equals(request.clearModelName())) {
            runner.setModelName(null);
        } else if (request.modelName() != null) {
            runner.setModelName(normalizeOptional(request.modelName()));
        }
        if (Boolean.TRUE.equals(request.clearModelVersion())) {
            runner.setModelVersion(null);
        } else if (request.modelVersion() != null) {
            runner.setModelVersion(normalizeOptional(request.modelVersion()));
        }
        if (Boolean.TRUE.equals(request.clearLlmApiKey())) {
            runner.setLlmApiKey(null);
        } else if (request.llmApiKey() != null) {
            runner.setLlmApiKey(normalizeOptional(request.llmApiKey()));
        }
        if (request.timeoutSeconds() != null) {
            runner.setTimeoutSeconds(request.timeoutSeconds());
        }
        if (request.maxConcurrentJobs() != null) {
            runner.setMaxConcurrentJobs(request.maxConcurrentJobs());
        }
        if (request.allowInternet() != null) {
            runner.setAllowInternet(request.allowInternet());
        }
        if (request.credentialMode() != null) {
            runner.setCredentialMode(request.credentialMode());
        }

        validateCredentialMode(runner);
        return agentRunnerRepository.save(runner);
    }

    @Transactional
    public void deleteRunner(WorkspaceContext workspaceContext, Long runnerId) {
        AgentRunner runner = agentRunnerRepository
            .findByIdAndWorkspaceId(runnerId, workspaceContext.id())
            .orElseThrow(() -> new EntityNotFoundException("AgentRunner", runnerId.toString()));

        long linkedConfigs = agentConfigRepository.countByRunnerId(runnerId);
        if (linkedConfigs > 0) {
            throw new AgentRunnerHasLinkedConfigsException(
                "Cannot delete runner with " + linkedConfigs + " linked agent config(s). Reassign or delete them first."
            );
        }

        agentRunnerRepository.delete(runner);
    }

    private void validateCredentialMode(AgentRunner runner) {
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

    private String normalizeRequiredName(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new IllegalArgumentException("Runner name is required");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
