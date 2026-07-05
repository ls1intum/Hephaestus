package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.core.security.EncryptedStringConverter;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiClient;
import de.tum.cit.aet.hephaestus.integration.outline.webhook.OutlineWebhookEvents;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Auto-registers (and tears down) the Outline change-notification subscription for a workspace, mirroring
 * {@code GitLabWebhookService}. On the first reconcile after connect it registers a subscription pointing at
 * {@code <externalUrl>/webhooks/outline} (the unified JetStream lane) with a freshly generated signing secret
 * and stores both the returned subscription id and the secret on the Connection
 * ({@link ConnectionConfig.OutlineConfig#withWebhookSubscription}). The signing secret never leaves the server
 * except as the shared secret Outline HMACs each delivery with.
 *
 * <p>Registration is idempotent (a workspace whose config already carries a subscription id is skipped) and
 * entirely best-effort: without a configured external URL, token, or on any Outline-side failure the periodic
 * six-hour reconcile remains the source of truth, so a missing subscription only costs freshness.
 */
@Component
@ConditionalOnProperty(name = "hephaestus.integration.outline.enabled", havingValue = "true", matchIfMissing = false)
public class OutlineWebhookRegistrar {

    private static final Logger log = LoggerFactory.getLogger(OutlineWebhookRegistrar.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** Subscription display name in the owner's Outline settings. */
    private static final String SUBSCRIPTION_NAME = "Hephaestus documentation sync";

    private final ConnectionService connectionService;
    private final OutlineApiClient outlineApiClient;
    private final EncryptedStringConverter secretCipher;
    private final String externalUrl;

    public OutlineWebhookRegistrar(
        ConnectionService connectionService,
        OutlineApiClient outlineApiClient,
        EncryptedStringConverter secretCipher,
        @Value("${hephaestus.webhook.external-url:}") String externalUrl
    ) {
        this.connectionService = connectionService;
        this.outlineApiClient = outlineApiClient;
        this.secretCipher = secretCipher;
        this.externalUrl = externalUrl;
    }

    /**
     * Registers a change-notification subscription for the workspace when one is not already stored. A no-op
     * (returning quietly) when the integration has no external URL, the workspace has no ACTIVE Outline
     * connection or token, or a subscription id is already recorded.
     */
    public void registerIfNeeded(long workspaceId) {
        if (externalUrl == null || externalUrl.isBlank()) {
            return;
        }
        Optional<Connection> active = connectionService.findActive(workspaceId, IntegrationKind.OUTLINE);
        if (active.isEmpty() || !(active.get().getConfig() instanceof ConnectionConfig.OutlineConfig config)) {
            return;
        }
        if (config.webhookSubscriptionId() != null && !config.webhookSubscriptionId().isBlank()) {
            return; // already registered
        }
        String serverUrl = config.serverUrl();
        if (serverUrl == null || serverUrl.isBlank()) {
            return;
        }
        Optional<BearerToken> bearer = connectionService.findActiveBearerToken(workspaceId, IntegrationKind.OUTLINE);
        if (bearer.isEmpty()) {
            return;
        }

        // Unified JetStream lane: deliveries land on WebhookController's /webhooks/{kind} entry, are
        // signature-verified, and are published to the durable `outline` stream (ADR 0023 §3).
        String deliveryUrl = externalUrl.replaceAll("/+$", "") + "/webhooks/outline";
        // Outline HMACs each delivery with the plaintext secret; only the AES-GCM ciphertext is stored, so the
        // signing secret matches the API token's at-rest posture rather than sitting readable in config JSONB.
        String signingSecret = generateSecret();
        String encryptedSecret = secretCipher.convertToDatabaseColumn(signingSecret);
        try {
            String subscriptionId = outlineApiClient.createWebhookSubscription(
                serverUrl,
                bearer.get().token(),
                SUBSCRIPTION_NAME,
                deliveryUrl,
                signingSecret,
                OutlineWebhookEvents.DOCUMENT_EVENTS
            );
            if (subscriptionId == null || subscriptionId.isBlank()) {
                log.warn("outline.webhook: register returned no subscription id for workspaceId={}", workspaceId);
                return;
            }
            connectionService.updateConfig(workspaceId, IntegrationKind.OUTLINE, cfg -> {
                if (!(cfg instanceof ConnectionConfig.OutlineConfig outlineCfg)) {
                    throw new IllegalStateException("Expected OutlineConfig on workspace=" + workspaceId);
                }
                return outlineCfg.withWebhookSubscription(subscriptionId, encryptedSecret);
            });
            log.info("outline.webhook: registered subscription {} for workspaceId={}", subscriptionId, workspaceId);
        } catch (RuntimeException e) {
            // Best-effort: the periodic reconcile still keeps the mirror fresh without a subscription.
            log.warn("outline.webhook: registration failed for workspaceId={}: {}", workspaceId, e.toString());
        }
    }

    /**
     * Best-effort delete of the workspace's change-notification subscription and clearing of the stored id and
     * secret. Never throws — a left-over subscription simply auto-disables upstream after repeated delivery
     * failures once the workspace is gone.
     */
    public void deregister(long workspaceId) {
        Optional<Connection> active = connectionService.findActive(workspaceId, IntegrationKind.OUTLINE);
        if (active.isEmpty() || !(active.get().getConfig() instanceof ConnectionConfig.OutlineConfig config)) {
            return;
        }
        String subscriptionId = config.webhookSubscriptionId();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return;
        }
        String serverUrl = config.serverUrl();
        Optional<BearerToken> bearer = connectionService.findActiveBearerToken(workspaceId, IntegrationKind.OUTLINE);
        if (serverUrl != null && !serverUrl.isBlank() && bearer.isPresent()) {
            try {
                outlineApiClient.deleteWebhookSubscription(serverUrl, bearer.get().token(), subscriptionId);
            } catch (RuntimeException e) {
                log.warn("outline.webhook: deregistration failed for workspaceId={}: {}", workspaceId, e.toString());
            }
        }
        try {
            connectionService.updateConfig(workspaceId, IntegrationKind.OUTLINE, cfg -> {
                if (!(cfg instanceof ConnectionConfig.OutlineConfig outlineCfg)) {
                    return cfg;
                }
                return outlineCfg.withWebhookSubscription(null, null);
            });
        } catch (RuntimeException e) {
            log.warn(
                "outline.webhook: clearing subscription fields failed for workspaceId={}: {}",
                workspaceId,
                e.toString()
            );
        }
    }

    /** A 256-bit hex signing secret (64 chars), comfortably above the NIST-recommended HMAC key length. */
    private static String generateSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
