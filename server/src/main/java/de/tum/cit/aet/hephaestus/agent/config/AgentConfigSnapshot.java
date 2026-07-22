package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot;
import org.jspecify.annotations.Nullable;

/** Audit snapshot of non-secret, supported agent-config settings and catalog bindings. */
record AgentConfigSnapshot(
    String name,
    boolean enabled,
    int timeoutSeconds,
    int maxConcurrentJobs,
    boolean allowInternet,
    @Nullable Long instanceModelId,
    @Nullable Long workspaceModelId
) implements ConfigAuditSnapshot {
    static AgentConfigSnapshot of(AgentConfig c) {
        return new AgentConfigSnapshot(
            c.getName(),
            c.isEnabled(),
            c.getTimeoutSeconds(),
            c.getMaxConcurrentJobs(),
            c.isAllowInternet(),
            c.getInstanceModel() != null ? c.getInstanceModel().getId() : null,
            c.getWorkspaceModel() != null ? c.getWorkspaceModel().getId() : null
        );
    }
}
