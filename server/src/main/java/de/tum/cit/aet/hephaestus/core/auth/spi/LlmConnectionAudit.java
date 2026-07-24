package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Cross-module port: record instance LLM connection changes on the auth-event trail (#1368). The
 * instance catalog is GLOBAL (app_admin-owned, not tenant-scoped), and
 * {@code config_audit_event.workspace_id} is NOT NULL, so a workspace-less change cannot land on that
 * ledger — it is audited here instead, the same ledger {@code AccountAdminController} already uses for
 * {@code APP_ROLE_CHANGED}.
 *
 * <p>{@code core.auth} implements this (over its {@code AuthEventLogger}, in
 * {@code LlmCatalogAuditAdapter}); {@code agent.catalog.LlmConnectionService} consumes it here rather
 * than reaching into {@code core.auth.audit} directly — {@code core.auth.spi} is the only
 * {@code core.auth} package other modules may depend on ({@code ModulithVerificationTest} enforces
 * this). Never pass the API key or a raw base URL — {@code slug} is the only free-text field.
 */
public interface LlmConnectionAudit {
    void connectionCreated(Long connectionId, String slug);

    void connectionUpdated(Long connectionId, String slug);

    void connectionDeleted(Long connectionId, String slug);
}
