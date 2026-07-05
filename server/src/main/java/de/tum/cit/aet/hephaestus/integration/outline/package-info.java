/**
 * Outline vendor adapter — mirrors allow-listed Outline collections into local documents and
 * projects them into the agent's context so design docs and decision records reach practice detection.
 *
 * <p>OPEN (matching {@code scm.github} / {@code scm.gitlab} / {@code slack}): the outbound boundary is
 * pinned through {@code allowedDependencies} as cross-module edges are introduced, so this adapter
 * cannot silently grow new imports. Documents are a content source, not a detection surface — Outline
 * never emits observations or findings of its own.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Integration · Outline",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    allowedDependencies = {
        // Connection registry + SPI ports: the credential provider, manifest, lifecycle listener,
        // and connect strategy plug into integration.core the same way every vendor adapter does.
        "integration.core",
        "integration.core::spi",
        // OutlineWebhookMessageHandler implements the unified IntegrationMessageHandler so Outline
        // change notifications ride the JetStream consumer lane (ADR 0023 §3), same as every SCM handler.
        "integration.core::handler",
        // OutlineApiClient uses the shared SSRF-guarded WebClient connector (core) + ServerUrlValidator
        // (core::security) because the Outline server URL is admin-supplied.
        "core",
        "core::security",
        // Runtime-role gate (@ConditionalOnServerRole) on the connect strategy.
        "core::runtime",
        // OutlineWorkspacePurgeAdapter implements the WorkspacePurgeContributor SPI so a workspace
        // PURGE cascades into a bulk delete of the mirrored outline_document rows.
        "workspace::spi",
        // integration.outline.documentation implements the agent-owned documentation-source SPI
        // (DocumentProjection): Outline owns the outline_document table and PROJECTS it to the agent through
        // this port, so the agent's mentor/review read path carries no raw SQL against the Outline schema. This
        // edge runs one way (integration.outline -> agent), the same direction as the Slack conversation-source
        // inversion, so no bounded-context cycle forms.
        "agent::documentation-source",
    }
)
package de.tum.cit.aet.hephaestus.integration.outline;
