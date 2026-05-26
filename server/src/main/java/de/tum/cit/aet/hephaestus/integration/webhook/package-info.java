/**
 * Inbound webhook reception — the shared receive-side substrate for every integration that
 * pushes events over HTTP. {@link
 * de.tum.cit.aet.hephaestus.integration.webhook.WebhookController} exposes a single
 * {@code POST /webhooks/{kind}} entry point; the {@code kind} path variable
 * ({@code github}, {@code gitlab}, {@code slack}, {@code outline}, …) selects the per-kind
 * {@link de.tum.cit.aet.hephaestus.integration.spi.WebhookSignatureVerifier} and
 * {@link de.tum.cit.aet.hephaestus.integration.spi.SubjectKeyDeriver} via {@link
 * de.tum.cit.aet.hephaestus.integration.webhook.IntegrationKindRouting}, and verified
 * envelopes are published to NATS JetStream for downstream fan-out. Bean wiring and gating
 * live on {@link de.tum.cit.aet.hephaestus.integration.webhook.WebhookConfiguration}.
 *
 * <p>Legacy per-kind paths ({@code /github}, {@code /gitlab}) were retired in pass 16/stage 2;
 * the unified {@code /webhooks/{kind}} URL is the only live entry point. The
 * {@link de.tum.cit.aet.hephaestus.integration.webhook.web.WebhookPayloadSizeFilter} is bound
 * to {@code /webhooks/*} only — no legacy paths are matched.
 *
 * <p>This is a cross-cutting integration trait — it is consumed by every vendor adapter
 * (SCM, messaging, knowledge, …) but contains no vendor-specific logic itself.
 */
@org.springframework.modulith.NamedInterface("webhook")
package de.tum.cit.aet.hephaestus.integration.webhook;
