package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Cross-module port: record instance LLM model changes on the auth-event trail. Same reasoning
 * as {@link LlmConnectionAudit} (GLOBAL catalog, {@code config_audit_event.workspace_id} is NOT NULL).
 *
 * <p>Implemented by {@code core.auth.audit.LlmCatalogAuditAdapter}.
 */
public interface LlmModelAudit {
    void modelCreated(Long modelId, Long connectionId, String slug);

    void modelUpdated(Long modelId, Long connectionId, String slug);

    void modelDeleted(Long modelId, Long connectionId, String slug);

    void modelPriceChanged(Long modelId, String pricingMode);

    void modelSharingChanged(Long modelId, String visibility, int workspaceCount);
}
