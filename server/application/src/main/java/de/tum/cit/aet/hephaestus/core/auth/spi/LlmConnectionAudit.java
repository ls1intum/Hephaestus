package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Cross-module port: record instance LLM connection changes on the auth-event trail. The instance
 * catalog is GLOBAL, and the config trail is workspace-scoped, so a workspace-less change cannot land
 * on that ledger.
 *
 * <p>Never pass the API key or a raw base URL — {@code slug} is the only free-text field.
 */
public interface LlmConnectionAudit {
    void connectionCreated(Long connectionId, String slug);

    void connectionUpdated(Long connectionId, String slug);

    void connectionDeleted(Long connectionId, String slug);
}
