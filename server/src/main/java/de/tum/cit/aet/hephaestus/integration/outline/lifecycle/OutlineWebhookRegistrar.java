package de.tum.cit.aet.hephaestus.integration.outline.lifecycle;

import de.tum.cit.aet.hephaestus.core.security.EncryptedStringConverter;
import de.tum.cit.aet.hephaestus.integration.core.connection.Connection;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.consumer.IntegrationNatsConsumer;
import de.tum.cit.aet.hephaestus.integration.core.spi.ApiCredentialProvider.BearerToken;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.outline.client.OutlineApiClient;
import de.tum.cit.aet.hephaestus.integration.outline.client.dto.OutlineWebhookSubscriptionListResponse;
import de.tum.cit.aet.hephaestus.integration.outline.webhook.OutlineWebhookEvents;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Registers (and tears down) the Outline change-notification subscription for a workspace, mirroring
 * {@code GitLabWebhookService}. Runs at connect time (via the connection-lifecycle listener) and as a
 * self-heal inside every full reconcile: Outline auto-disables a subscription after repeated delivery
 * failures, so {@link #ensureSubscription} verifies a stored id upstream and re-registers when the
 * subscription went missing or was disabled. The subscription points at
 * {@code <externalUrl>/webhooks/outline} (the unified JetStream lane) with a freshly generated signing
 * secret; both the returned subscription id and the secret are stored on the Connection
 * ({@link ConnectionConfig.OutlineConfig#withWebhookSubscription}). The signing secret never leaves the
 * server except as the shared secret Outline HMACs each delivery with.
 *
 * <p>Everything here is best-effort and never throws: without a configured external URL, token, or on
 * any Outline-side failure the periodic reconcile remains the source of truth, so a missing
 * subscription only costs freshness.
 *
 * <p>This is also the single seam through which a workspace's outline subscription id changes — fresh
 * registration, self-heal re-registration (a new id replaces the old one), and both deregister paths all
 * run through this class. The workspace-side {@code NatsSubscriptionProvider} derives the {@code outline}
 * stream subject from that stored id, so every id change must be followed by a scope-consumer reconcile or
 * the running consumer keeps filtering on a stale (or absent) subject: without it, a workspace that
 * connects Outline while the server is already running would never receive its webhook deliveries until
 * the next restart. {@link #reconcileScopeConsumer} is called from every branch that actually changes what
 * the subscription provider would report, never from the early no-op returns.
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

    /**
     * Absent when {@code hephaestus.sync.nats.enabled=false} (the consumer bean is conditional on it);
     * the registrar itself carries no such requirement, so every use goes through {@link
     * ObjectProvider#ifAvailable}.
     */
    private final ObjectProvider<IntegrationNatsConsumer> natsConsumer;

    public OutlineWebhookRegistrar(
        ConnectionService connectionService,
        OutlineApiClient outlineApiClient,
        EncryptedStringConverter secretCipher,
        @Value("${hephaestus.webhook.external-url:}") String externalUrl,
        ObjectProvider<IntegrationNatsConsumer> natsConsumer
    ) {
        this.connectionService = connectionService;
        this.outlineApiClient = outlineApiClient;
        this.secretCipher = secretCipher;
        this.externalUrl = externalUrl;
        this.natsConsumer = natsConsumer;
    }

    /**
     * Brings the workspace's scope consumer in line with the subscription id this class just stored or
     * invalidated. {@code startConsumingScope} no-ops once the scope already has consumers (the common
     * case: an SCM stream is already running) and {@code updateScopeConsumer} no-ops when the scope has
     * none yet (the rare case: Outline is the workspace's first-ever integration) — calling both in
     * sequence therefore reconciles every state without duplicating work in either.
     */
    private void reconcileScopeConsumer(long workspaceId) {
        natsConsumer.ifAvailable(consumer -> {
            consumer.startConsumingScope(workspaceId);
            consumer.updateScopeConsumer(workspaceId);
        });
    }

    /**
     * Ensures the workspace has a live change-notification subscription. Without a stored id this
     * registers one (a quiet no-op when the integration has no external URL, ACTIVE Outline connection,
     * server URL, or token). With a stored id it verifies the subscription upstream: gone or
     * {@code enabled=false} (Outline auto-disables after 25 consecutive delivery failures) drops the
     * stale id + secret and registers fresh; an unverifiable upstream (listing failed) changes nothing.
     */
    public void ensureSubscription(long workspaceId) {
        if (externalUrl == null || externalUrl.isBlank()) {
            return;
        }
        Optional<Connection> active = connectionService.findActive(workspaceId, IntegrationKind.OUTLINE);
        if (active.isEmpty() || !(active.get().getConfig() instanceof ConnectionConfig.OutlineConfig config)) {
            return;
        }
        String serverUrl = config.serverUrl();
        if (serverUrl == null || serverUrl.isBlank()) {
            return;
        }
        Optional<BearerToken> bearer = connectionService.findActiveBearerToken(workspaceId, IntegrationKind.OUTLINE);
        if (bearer.isEmpty()) {
            return;
        }
        String token = bearer.get().token();

        String storedId = config.webhookSubscriptionId();
        if (storedId != null && !storedId.isBlank()) {
            Boolean healthy = isSubscriptionHealthy(serverUrl, token, storedId);
            if (healthy == null || healthy) {
                return; // healthy, or unverifiable — don't churn a subscription we cannot see
            }
            log.info(
                "outline.webhook: stored subscription {} is missing/disabled upstream for workspaceId={} — re-registering",
                storedId,
                workspaceId
            );
            clearStoredSubscription(workspaceId);
        }
        register(workspaceId, serverUrl, token);
    }

    /**
     * Whether the stored subscription still exists upstream and is enabled. {@code null} when the
     * upstream listing itself failed (unknown — the caller must not churn on that).
     */
    private Boolean isSubscriptionHealthy(String serverUrl, String token, String subscriptionId) {
        try {
            for (OutlineWebhookSubscriptionListResponse.Subscription subscription : outlineApiClient.listWebhookSubscriptions(
                serverUrl,
                token
            )) {
                if (subscriptionId.equals(subscription.id())) {
                    return !Boolean.FALSE.equals(subscription.enabled());
                }
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("outline.webhook: could not verify subscription {}: {}", subscriptionId, e.toString());
            return null;
        }
    }

    private void register(long workspaceId, String serverUrl, String token) {
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
                token,
                SUBSCRIPTION_NAME,
                deliveryUrl,
                signingSecret,
                OutlineWebhookEvents.SUBSCRIBED_EVENTS
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
            // The subject the scope consumer filters on is derived from this id — reconcile now instead of
            // waiting for a restart (fresh register) or the next 6h reconcile (self-heal re-register).
            reconcileScopeConsumer(workspaceId);
        } catch (RuntimeException e) {
            // Best-effort: the periodic reconcile still keeps the mirror fresh without a subscription.
            log.warn("outline.webhook: registration failed for workspaceId={}: {}", workspaceId, e.toString());
        }
    }

    private void clearStoredSubscription(long workspaceId) {
        try {
            connectionService.updateConfig(workspaceId, IntegrationKind.OUTLINE, cfg -> {
                if (!(cfg instanceof ConnectionConfig.OutlineConfig outlineCfg)) {
                    return cfg;
                }
                return outlineCfg.withWebhookSubscription(null, null);
            });
        } catch (RuntimeException e) {
            log.warn(
                "outline.webhook: clearing stale subscription fields failed for workspaceId={}: {}",
                workspaceId,
                e.toString()
            );
        }
    }

    /**
     * Best-effort upstream delete of the workspace's change-notification subscription, resolved through
     * the ACTIVE connection. Never throws — a left-over subscription auto-disables upstream after
     * repeated delivery failures once the workspace is gone.
     *
     * <p>Deliberately does NOT clear the stored id/secret: both callers (connect-strategy revoke inside
     * the disconnect request, and the workspace purge) run while another transaction holds the same
     * Connection entity — a config rewrite here bumps the row's version and the caller's subsequent
     * save throws {@code ObjectOptimisticLockingFailureException}. The stored fields are inert on a
     * torn-down row, and reactivation's {@link #ensureSubscription} self-heal replaces a stale id.
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
        // Harmless here (the connection is still ACTIVE, so the subscription provider's view is
        // unchanged) but required symmetry: this method runs before the caller's own state transition
        // takes the connection off ACTIVE, and the {@code deregister(workspaceId, connectionId)}
        // variant — called after that transition commits — does the reconcile that actually matters.
        reconcileScopeConsumer(workspaceId);
    }

    /**
     * Deactivation-time variant: the connection just left ACTIVE, so it is resolved by id regardless of
     * state. SUSPENDED connections still carry credentials and get a real upstream delete; UNINSTALLED
     * ones had their credentials purged — then this only logs that the orphaned subscription will
     * auto-disable upstream. The stored id/secret stay on the row (the ACTIVE-scoped config mutator no
     * longer reaches it); reactivation's {@link #ensureSubscription} self-heal replaces them.
     */
    public void deregister(long workspaceId, long connectionId) {
        Optional<Connection> connection = connectionService.findInWorkspace(workspaceId, connectionId);
        if (connection.isEmpty() || !(connection.get().getConfig() instanceof ConnectionConfig.OutlineConfig config)) {
            return;
        }
        String subscriptionId = config.webhookSubscriptionId();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return;
        }
        String serverUrl = config.serverUrl();
        Optional<BearerToken> bearer = connectionService.findBearerToken(workspaceId, connectionId);
        if (serverUrl == null || serverUrl.isBlank() || bearer.isEmpty()) {
            log.info(
                "outline.webhook: no usable credentials to delete subscription {} for connectionId={} — " +
                    "it will auto-disable upstream after repeated delivery failures",
                subscriptionId,
                connectionId
            );
            // The connection already left ACTIVE (that's why this by-id variant is resolving it), so the
            // subscription provider has already stopped reporting the outline stream for this workspace —
            // reconcile the scope consumer down to match even though the upstream delete itself is a no-op.
            reconcileScopeConsumer(workspaceId);
            return;
        }
        try {
            outlineApiClient.deleteWebhookSubscription(serverUrl, bearer.get().token(), subscriptionId);
            log.info(
                "outline.webhook: deleted subscription {} for deactivated connectionId={}",
                subscriptionId,
                connectionId
            );
        } catch (RuntimeException e) {
            log.warn("outline.webhook: deregistration failed for connectionId={}: {}", connectionId, e.toString());
        }
        reconcileScopeConsumer(workspaceId);
    }

    /** A 256-bit hex signing secret (64 chars), comfortably above the NIST-recommended HMAC key length. */
    private static String generateSecret() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
