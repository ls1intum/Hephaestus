package de.tum.cit.aet.hephaestus.core.auth.spi;

/**
 * Cross-module port: record instance LLM governance settings changes on the auth-event trail, for the
 * reason given on {@link LlmConnectionAudit}.
 *
 * <p>Consumed through an {@code ObjectProvider}: the only implementation is
 * {@code @ConditionalOnServerRole}, while its callers load on every runtime role.
 */
public interface LlmSettingsAudit {
    void settingsChanged(boolean allowWorkspaceConnections);
}
