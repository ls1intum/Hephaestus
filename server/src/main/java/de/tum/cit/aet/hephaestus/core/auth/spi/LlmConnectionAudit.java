package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Cross-module port: record instance LLM connection changes on the auth-event trail. The
 * instance catalog is GLOBAL (app_admin-owned, not tenant-scoped), and
 * {@code config_audit_event.workspace_id} is NOT NULL, so a workspace-less change cannot land on that
 * ledger — it is audited here instead, the same ledger {@code AccountAdminController} already uses for
 * {@code APP_ROLE_CHANGED}.
 *
 * <p>Implemented by {@code core.auth.audit.LlmCatalogAuditAdapter}. Never pass the API key or a raw
 * base URL — {@code slug} is the only free-text field.
 */
public interface LlmConnectionAudit {
    void connectionCreated(Long connectionId, String slug);

    void connectionUpdated(Long connectionId, String slug);

    void connectionDeleted(Long connectionId, String slug);
}
