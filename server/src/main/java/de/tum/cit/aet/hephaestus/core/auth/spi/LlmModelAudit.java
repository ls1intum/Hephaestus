package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Cross-module port: record instance LLM model changes on the auth-event trail (#1368). Same reasoning
 * as {@link LlmConnectionAudit} (GLOBAL catalog, {@code config_audit_event.workspace_id} is NOT NULL).
 *
 * <p>{@code core.auth} implements this (over its {@code AuthEventLogger}, in
 * {@code LlmCatalogAuditAdapter}); {@code agent.catalog.LlmModelService} consumes it here rather than
 * reaching into {@code core.auth.audit} directly.
 */
public interface LlmModelAudit {
    void modelCreated(Long modelId, Long connectionId, String slug);

    void modelUpdated(Long modelId, Long connectionId, String slug);

    void modelDeleted(Long modelId, Long connectionId, String slug);

    void modelPriceChanged(Long modelId, String pricingMode);

    void modelSharingChanged(Long modelId, String visibility, int workspaceCount);
}
