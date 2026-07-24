package de.tum.cit.aet.hephaestus.core.auth.audit;

import de.tum.cit.aet.hephaestus.core.auth.spi.LlmConnectionAudit;
import de.tum.cit.aet.hephaestus.core.auth.spi.LlmModelAudit;
import de.tum.cit.aet.hephaestus.core.auth.spi.LlmSettingsAudit;
import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import de.tum.cit.aet.hephaestus.core.security.SecurityUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * In-{@code core.auth} implementation of {@link LlmConnectionAudit}, {@link LlmModelAudit} and
 * {@link LlmSettingsAudit} (#1368) — mirrors {@link ResearchConsentAuditAdapter}'s role, but for the
 * instance LLM catalog rather than research consent. One adapter class for all three ports (they share
 * the {@link AuthEventLogger} plumbing and the JSON-escaping helper below); the ports themselves stay
 * split by consumer so each interface stays under the SPI method-count ceiling
 * ({@code CodeQualityTest.spiInterfacesAreFocused}). Keeps {@link AuthEventLogger} + the {@code LLM_*}
 * event types encapsulated inside {@code core.auth}; {@code agent.catalog} consumes only the SPI ports.
 *
 * <p>Best-effort by contract — {@link AuthEventLogger.Draft#record()} already swallows its own
 * failures, so an audit write never breaks the catalog mutation it describes.
 */
@ConditionalOnServerRole
@Component
public class LlmCatalogAuditAdapter implements LlmConnectionAudit, LlmModelAudit, LlmSettingsAudit {

    private final AuthEventLogger authEventLogger;

    public LlmCatalogAuditAdapter(AuthEventLogger authEventLogger) {
        this.authEventLogger = authEventLogger;
    }

    @Override
    public void connectionCreated(Long connectionId, String slug) {
        record(AuthEvent.EventType.LLM_CONNECTION_CREATED, connectionDetails(connectionId, slug));
    }

    @Override
    public void connectionUpdated(Long connectionId, String slug) {
        record(AuthEvent.EventType.LLM_CONNECTION_UPDATED, connectionDetails(connectionId, slug));
    }

    @Override
    public void connectionDeleted(Long connectionId, String slug) {
        record(AuthEvent.EventType.LLM_CONNECTION_DELETED, connectionDetails(connectionId, slug));
    }

    @Override
    public void modelCreated(Long modelId, Long connectionId, String slug) {
        record(AuthEvent.EventType.LLM_MODEL_CREATED, modelDetails(modelId, connectionId, slug));
    }

    @Override
    public void modelUpdated(Long modelId, Long connectionId, String slug) {
        record(AuthEvent.EventType.LLM_MODEL_UPDATED, modelDetails(modelId, connectionId, slug));
    }

    @Override
    public void modelDeleted(Long modelId, Long connectionId, String slug) {
        record(AuthEvent.EventType.LLM_MODEL_DELETED, modelDetails(modelId, connectionId, slug));
    }

    @Override
    public void modelPriceChanged(Long modelId, String pricingMode) {
        record(
            AuthEvent.EventType.LLM_MODEL_PRICE_CHANGED,
            "{\"modelId\":" + modelId + ",\"pricingMode\":\"" + jsonEscape(pricingMode) + "\"}"
        );
    }

    @Override
    public void modelSharingChanged(Long modelId, String visibility, int workspaceCount) {
        record(
            AuthEvent.EventType.LLM_MODEL_SHARING_CHANGED,
            "{\"modelId\":" +
                modelId +
                ",\"visibility\":\"" +
                jsonEscape(visibility) +
                "\",\"workspaceCount\":" +
                workspaceCount +
                "}"
        );
    }

    @Override
    public void settingsChanged(boolean allowWorkspaceConnections) {
        record(
            AuthEvent.EventType.LLM_SETTINGS_CHANGED,
            "{\"allowWorkspaceConnections\":" + allowWorkspaceConnections + "}"
        );
    }

    private void record(AuthEvent.EventType type, String details) {
        authEventLogger
            .event(type, AuthEvent.Result.SUCCESS)
            .actingAccount(SecurityUtils.getCurrentAccountId().orElse(null))
            .details(details)
            .record();
    }

    private static String connectionDetails(Long connectionId, String slug) {
        return "{\"connectionId\":" + connectionId + ",\"slug\":\"" + jsonEscape(slug) + "\"}";
    }

    private static String modelDetails(Long modelId, Long connectionId, String slug) {
        return (
            "{\"modelId\":" + modelId + ",\"connectionId\":" + connectionId + ",\"slug\":\"" + jsonEscape(slug) + "\"}"
        );
    }

    /** Minimal JSON string escaping for the free-text values embedded in the {@code details} object. */
    private static String jsonEscape(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
