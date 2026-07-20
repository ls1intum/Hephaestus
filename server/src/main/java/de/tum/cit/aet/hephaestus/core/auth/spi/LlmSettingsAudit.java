package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Cross-module port: record instance LLM governance settings changes on the auth-event trail (#1368).
 * Same reasoning as {@link LlmConnectionAudit} (GLOBAL, {@code config_audit_event.workspace_id} is NOT
 * NULL).
 *
 * <p>{@code core.auth} implements this (over its {@code AuthEventLogger}, in
 * {@code LlmCatalogAuditAdapter}); {@code agent.catalog.InstanceLlmSettingsService} consumes it via an
 * {@code ObjectProvider} — that service is also called by the ungated workspace BYO services, which
 * load on every runtime role, so it cannot carry a hard dependency on a
 * {@code @ConditionalOnServerRole} implementation.
 */
public interface LlmSettingsAudit {
    void settingsChanged(boolean allowWorkspaceConnections, String defaultUnpricedPolicy);
}
