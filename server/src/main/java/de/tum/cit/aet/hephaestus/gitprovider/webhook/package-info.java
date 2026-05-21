/**
 * Inbound webhook reception — {@code /gitlab} and {@code /github} endpoints, HMAC/token
 * verification, NATS JetStream publishing. Sub-package of the {@code gitprovider} Modulith module.
 * Bean wiring and gating live on
 * {@link de.tum.cit.aet.hephaestus.gitprovider.webhook.WebhookConfiguration}.
 */
package de.tum.cit.aet.hephaestus.gitprovider.webhook;
