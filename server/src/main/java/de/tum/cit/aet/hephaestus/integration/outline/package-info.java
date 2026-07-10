/**
 * Outline vendor adapter — mirrors allow-listed Outline collections into local documents and
 * projects them into the agent's context so design docs and decision records reach practice detection.
 *
 * <p>A closed module (the Modulith default): nothing outside consumes Outline types — the agent reads
 * through its own {@code agent::documentation-source} SPI, which this module merely implements — so no
 * package is exposed. The outbound boundary is pinned through {@code allowedDependencies} as
 * cross-module edges are introduced, so this adapter cannot silently grow new imports. Documents are a
 * content source, not a detection surface — Outline never emits observations or findings of its own.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Integration · Outline",
    allowedDependencies = {
        // Connection registry + SPI ports: the credential provider, manifest, lifecycle listener,
        // and connect strategy plug into integration.core the same way every vendor adapter does.
        "integration.core",
        "integration.core::spi",
        // OutlineWebhookMessageHandler implements the unified IntegrationMessageHandler so Outline
        // change notifications ride the JetStream consumer lane (ADR 0023 §3), same as every SCM handler.
        "integration.core::handler",
        // OutlineConnectionStateListener consumes the ConnectionLifecycleEvent seam published from
        // ConnectionService.transition() (register webhook on activate, deregister on deactivate).
        "integration.core::events",
        // OutlineApiClient uses the shared SSRF-guarded WebClient connector (core) + ServerUrlValidator
        // (core::security) because the Outline server URL is admin-supplied.
        "core",
        "core::security",
        // Runtime-role gate (@ConditionalOnServerRole) on the connect strategy.
        "core::runtime",
        // The admin services throw the shared EntityNotFoundException so a workspace without an
        // ACTIVE Outline connection (or an unregistered collection) maps to 404 via the global advice.
        "core::exception",
        // OutlineWorkspacePurgeAdapter implements the WorkspacePurgeContributor SPI so a workspace
        // PURGE cascades into a bulk delete of the mirrored outline_document rows.
        "workspace::spi",
        // The admin controllers (connect + collection control plane) sit behind the workspace admin
        // guard (authorization) and resolve the workspace via @WorkspaceScopedController /
        // WorkspaceContext (context) — the same edges the Slack admin surface uses.
        "workspace::authorization",
        "workspace::context",
        // OutlineIdentityResolver resolves a document author (server, team, Outline user UUID) to the
        // workspace developer through the auth SPI ports only (GitProviderRegistry + AccountIdentityQuery +
        // AccountWorkspaceMembershipQuery, whose membership view carries the member's SCM User id) — never
        // core.auth or SCM domain types. Mirrors the Slack mentor resolver.
        "core::auth-spi",
        // integration.outline.documentation implements the agent-owned documentation-source SPI
        // (DocumentProjection): Outline owns the outline_document table and PROJECTS it to the agent through
        // this port, so the agent's mentor/review read path carries no raw SQL against the Outline schema. This
        // edge runs one way (integration.outline -> agent), the same direction as the Slack conversation-source
        // inversion, so no bounded-context cycle forms.
        "agent::documentation-source",
    }
)
package de.tum.cit.aet.hephaestus.integration.outline;
