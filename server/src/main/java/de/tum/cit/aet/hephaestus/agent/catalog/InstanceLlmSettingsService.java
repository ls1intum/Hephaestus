package de.tum.cit.aet.hephaestus.agent.catalog;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.core.auth.spi.LlmSettingsAudit;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read/write access to the instance LLM settings singleton (#1368). GLOBAL: gated by
 * {@code app_admin} on {@link InstanceLlmSettingsController}, so this service is
 * {@link WorkspaceAgnostic}.
 *
 * <p>Unlike {@link LlmConnectionService}/{@link LlmModelService}, this service stays UNGATED: it is
 * also consumed by {@link WorkspaceLlmConnectionService} and {@link WorkspaceLlmModelService} (for the
 * {@code allow_workspace_connections} BYO gate), which load unconditionally on every runtime role. A
 * hard {@link LlmSettingsAudit} dependency here would break their context refresh on worker/webhook
 * (the port's sole implementation is {@code @ConditionalOnServerRole}, matching how
 * {@code AccountPreferencesService} consumes {@code ResearchConsentAudit} via {@link ObjectProvider}
 * for the same reason).
 */
@Service
@RequiredArgsConstructor
@WorkspaceAgnostic("Instance LLM settings singleton is global (app_admin-owned), not tenant-scoped")
public class InstanceLlmSettingsService {

    static final short SINGLETON_ID = 1;

    private final InstanceLlmSettingsRepository settingsRepository;
    private final ObjectProvider<LlmSettingsAudit> llmSettingsAuditProvider;

    @Transactional(readOnly = true)
    public InstanceLlmSettings get() {
        return settingsRepository.findById(SINGLETON_ID).orElseGet(InstanceLlmSettingsService::defaults);
    }

    @Transactional
    public InstanceLlmSettings update(UpdateInstanceLlmSettingsRequestDTO request) {
        InstanceLlmSettings settings = settingsRepository
            .findById(SINGLETON_ID)
            .orElseGet(() -> {
                InstanceLlmSettings created = defaults();
                created.setId(SINGLETON_ID);
                return created;
            });

        if (request.allowedEgressHosts() != null) {
            String hosts = request.allowedEgressHosts().isBlank() ? null : request.allowedEgressHosts().trim();
            settings.setAllowedEgressHosts(hosts);
        }
        if (request.allowWorkspaceConnections() != null) {
            settings.setAllowWorkspaceConnections(request.allowWorkspaceConnections());
        }
        if (request.defaultUnpricedPolicy() != null) {
            settings.setDefaultUnpricedPolicy(request.defaultUnpricedPolicy());
        }

        settings.setUpdatedAt(Instant.now());
        settings.setUpdatedBy(SecurityUtils.getCurrentUserLogin().orElse(null));
        InstanceLlmSettings saved = settingsRepository.save(settings);

        LlmSettingsAudit llmSettingsAudit = llmSettingsAuditProvider.getIfAvailable();
        if (llmSettingsAudit != null) {
            llmSettingsAudit.settingsChanged(saved.isAllowWorkspaceConnections(), saved.getDefaultUnpricedPolicy());
        }
        return saved;
    }

    private static InstanceLlmSettings defaults() {
        InstanceLlmSettings settings = new InstanceLlmSettings();
        settings.setId(SINGLETON_ID);
        settings.setAllowWorkspaceConnections(true);
        settings.setDefaultUnpricedPolicy("WARN");
        return settings;
    }
}
