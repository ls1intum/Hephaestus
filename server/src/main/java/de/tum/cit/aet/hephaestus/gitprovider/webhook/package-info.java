/**
 * Inbound webhook reception — {@code /gitlab} and {@code /github} endpoints, HMAC/token
 * verification, NATS JetStream publishing. Sub-package of the {@code gitprovider} Modulith module
 * (declared {@code Type.OPEN} on its own {@code package-info.java}).
 *
 * <p>The bean chain is gated by {@code @ConditionalOnProperty(WEBHOOK_PROPERTY, matchIfMissing=true)}
 * on {@link de.tum.cit.aet.hephaestus.gitprovider.webhook.WebhookConfiguration}; production sets
 * it {@code false} on {@code application-server} and {@code true} on {@code webhook-server} (one
 * image, two containers — see ADR 0008).
 *
 * <p>Pure verifiers/builders have no Spring dependencies. NATS Connection is REUSED from
 * {@code config.NatsConfig.natsConnection}.
 */
package de.tum.cit.aet.hephaestus.gitprovider.webhook;
