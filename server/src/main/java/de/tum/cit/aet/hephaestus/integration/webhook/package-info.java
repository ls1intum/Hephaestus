/**
 * Inbound webhook reception — the shared receive-side substrate for every integration that
 * pushes events over HTTP. Exposes {@code /gitlab}, {@code /github}, {@code /slack},
 * {@code /outline}, … endpoints under a single {@link
 * de.tum.cit.aet.hephaestus.integration.webhook.WebhookController}; verifies HMAC / token
 * signatures per-kind via {@link
 * de.tum.cit.aet.hephaestus.integration.webhook.IntegrationKindRouting}; publishes verified
 * envelopes to NATS JetStream for downstream fan-out. Bean wiring and gating live on
 * {@link de.tum.cit.aet.hephaestus.integration.webhook.WebhookConfiguration}.
 *
 * <p>This is a cross-cutting integration trait — it is consumed by every vendor adapter
 * (SCM, messaging, knowledge, …) but contains no vendor-specific logic itself.
 */
package de.tum.cit.aet.hephaestus.integration.webhook;
