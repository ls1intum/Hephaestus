package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the {@link DefaultAgentConfigProperties}-described model in the default workspace once
 * workspaces exist ({@link WorkspacesInitializedEvent} — the same hook integration consumers use,
 * so it fires after async provisioning, never racing it). Idempotent: skips when disabled, when no
 * API key is set, when no workspace exists, or when a config of the same name is already present.
 */
@Component
class DefaultAgentConfigSeeder {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentConfigSeeder.class);

    private final DefaultAgentConfigProperties properties;
    private final AgentConfigService agentConfigService;
    private final AgentConfigRepository agentConfigRepository;
    private final WorkspaceRepository workspaceRepository;

    DefaultAgentConfigSeeder(
        DefaultAgentConfigProperties properties,
        AgentConfigService agentConfigService,
        AgentConfigRepository agentConfigRepository,
        WorkspaceRepository workspaceRepository
    ) {
        this.properties = properties;
        this.agentConfigService = agentConfigService;
        this.agentConfigRepository = agentConfigRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @EventListener(WorkspacesInitializedEvent.class)
    @Transactional
    public void seed() {
        if (!properties.enabled()) {
            return;
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("Default agent config enabled but hephaestus.agent.default-config.api-key is unset; skipping.");
            return;
        }
        Workspace workspace = workspaceRepository.findAll().stream().findFirst().orElse(null);
        if (workspace == null) {
            log.warn("Default agent config enabled but no workspace exists yet; skipping.");
            return;
        }
        if (agentConfigRepository.existsByWorkspaceIdAndName(workspace.getId(), properties.name())) {
            return;
        }
        var request = CreateAgentConfigRequestDTO.builder()
            .name(properties.name())
            .llmProvider(properties.provider())
            .modelName(properties.modelName())
            .llmApiKey(properties.apiKey())
            .enabled(true)
            .credentialMode(CredentialMode.PROXY)
            .build();
        var created = agentConfigService.createConfig(
            WorkspaceContext.fromWorkspace(workspace, Set.of(), null),
            request
        );
        log.info(
            "Seeded default agent config: name={}, provider={}, workspaceId={}, id={}",
            created.getName(),
            properties.provider(),
            workspace.getId(),
            created.getId()
        );
    }
}
