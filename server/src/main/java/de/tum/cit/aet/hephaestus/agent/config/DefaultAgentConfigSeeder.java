package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import de.tum.cit.aet.hephaestus.workspace.context.WorkspaceContext;
import java.util.Comparator;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates the {@link DefaultAgentConfigProperties}-described model in the default workspace once
 * workspaces exist ({@link WorkspacesInitializedEvent}). Idempotent: skips when disabled, when no
 * API key is set, when no workspace exists, or when a config of the same name already exists.
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
    public void seed() {
        if (!properties.enabled()) {
            return;
        }
        // The publisher (WorkspaceStartupListener) calls this inline, then runs workspace activation;
        // isolate failures so a bad seed can't abort the rest of startup. createConfig manages its own
        // transaction, so no @Transactional is needed here.
        try {
            seedDefaultConfig();
        } catch (RuntimeException e) {
            log.error("Default agent config seeding failed; continuing startup.", e);
        }
    }

    private void seedDefaultConfig() {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("Default agent config enabled but hephaestus.agent.default-config.api-key is unset; skipping.");
            return;
        }
        // findAll() has no guaranteed order, so on a multi-workspace instance the "default" target would be
        // non-deterministic across boots — and the existsBy idempotency check and the create could even land
        // on different rows. Pin to the oldest workspace (lowest id) so the pick is stable.
        Workspace workspace = workspaceRepository
            .findAll()
            .stream()
            .min(Comparator.comparing(Workspace::getId))
            .orElse(null);
        if (workspace == null) {
            log.warn("Default agent config enabled but no workspace exists yet; skipping.");
            return;
        }
        if (agentConfigRepository.existsByWorkspaceIdAndName(workspace.getId(), properties.name())) {
            return;
        }
        if (properties.modelName() == null || properties.modelName().isBlank()) {
            // createConfig leaves model_name null when modelName is null, producing an enabled config that is
            // unlikely to run. Unlike the missing-api-key case we still seed it (the model can be set later in
            // the UI), but surface the misconfiguration loudly rather than failing silently at first job.
            log.warn(
                "Default agent config has no model-name (hephaestus.agent.default-config.model-name is unset); " +
                    "seeding an enabled config without a model — set a model before it can run."
            );
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
