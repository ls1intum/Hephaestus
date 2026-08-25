package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Cross-module port: record instance LLM model changes on the auth-event trail, for the reason given
 * on {@link LlmConnectionAudit}.
 */
public interface LlmModelAudit {
    void modelCreated(Long modelId, Long connectionId, String slug);

    void modelUpdated(Long modelId, Long connectionId, String slug);

    void modelDeleted(Long modelId, Long connectionId, String slug);

    void modelPriceChanged(Long modelId, String pricingMode);

    void modelSharingChanged(Long modelId, String visibility, int workspaceCount);
}
