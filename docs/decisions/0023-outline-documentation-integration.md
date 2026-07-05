# ADR 0023: Outline documentation integration — a content source, not a detection surface

**Status:** Proposed
**Date:** 2026-07-04
**Authors:** Felix T.J. Dietrich
**Builds on:** [ADR 0015](0015-unified-integration-framework.md) (integration framework), [ADR 0020](0020-context-fabric-everything-is-an-integration.md) (context fabric), [ADR 0014](0014-per-row-aes-gcm-aad-binding.md) (credential encryption), [ADR 0007](0007-sandbox-spi-shape.md) (sandbox SPI shape)

## Context

Team wikis (Outline) hold the design docs, ADRs, and decision records that a good engineering
practice produces — yet none of that ever reaches practice detection. Mentor feedback on
"records significant decisions with rationale" or "documents public API and behaviour changes"
is blind to the very artifacts those practices are about. Making an allow-listed set of Outline
collections available as **context** lets existing practices reason over the docs a change refers
to, without inventing any new detection surface.

Outline is the first vendor in a documentation family (Confluence and Notion share the shape), and
the first integration that is purely a **content source**: it never emits observations or findings.
This ADR records the handful of decisions where Outline deliberately diverges from the SCM and
messaging integrations already in the tree.

## Decision drivers

- A documentation vendor extends the integration framework without forcing a change to the
  detection or feedback schema.
- The server calls an admin-supplied server URL, so server-side request forgery is a first-class
  threat that must be closed with the guards already in the tree, not a new bespoke one.
- Docs are deliberately-published, team-authored artifacts read by an admin-authorized bot against
  an explicit allow-list — a materially different privacy profile from live chat ingestion.
- Adding a vendor should stay flat in cost (ADR 0015) and not re-open settled abstractions.

## Decisions

### 1. A new family `DOCUMENTATION` and kind `OUTLINE`

`IntegrationFamily` gains `DOCUMENTATION` alongside `SCM` and `MESSAGING`, and `IntegrationKind`
gains `OUTLINE`. A family — not just a kind — because the documentation category spans multiple
vendors (Outline, Confluence, Notion) that share the same content-source shape, the same
`outline_document`-style mirror, and the same agent projection seam. Family membership is what lets
cross-cutting logic (context assembly, retention) treat "a documentation source" uniformly.

### 2. Content source, not detection surface

Outline contributes context; it produces no observations, findings, or reactions. There are no
pre-baked Outline doc-quality practices in this iteration (an explicit non-goal). Because nothing
touches the observation/finding schema, [ADR 0021](0021-findings-feedback-synthesis-seam.md) and
[ADR 0022](0022-observation-presence-assessment-and-schema-cleanup.md) are untouched. Documents are
mirrored into `outline_document` and projected to the agent through an agent-owned
`DocumentProjection` port; the agent read path carries no raw SQL against Outline's schema.

### 3. `WEBHOOK_INGEST` capability on the unified JetStream lane

Outline change notifications ride the same durable `/webhooks/{kind}` JetStream lane as GitHub and
GitLab. `OutlineManifest.declaredCapabilities()` is `{WEBHOOK_INGEST}`, which binds the four per-kind
SPI beans the framework bootstrap validates: `OutlineWebhookSignatureVerifier` (implements
`WebhookSignatureVerifier`), `OutlineWebhookSecretSource` (implements `WebhookSecretSource`,
`Scope.SUBSCRIPTION`), `OutlineSubjectKeyDeriver`, and `OutlineSubjectParser`. The registrar points
the subscription's delivery URL at `POST /webhooks/outline`; the webhook-role `WebhookController`
verifies the HMAC and publishes to the `outline` JetStream stream, and the server-role consumer fleet
subscribes to `outline.<subscriptionId>.>` and hands each event to `OutlineWebhookMessageHandler`
(an `IntegrationMessageHandler`) which reconciles the named workspace. Riding the durable lane is the
point: an app-server redeploy no longer drops in-flight deliveries (the reason ADR 0008 decoupled the
webhook role), and a handler failure reaches the poison handler (NAK + backoff, ACK-after-N) instead
of a swallowed catch.

Each delivery names its subscription in the body. That subscription id is an **untrusted routing
key**: `OutlineWebhookSecretSource` parses it out of the body (not a header, so the secret source is
`Scope.SUBSCRIPTION`) and resolves the stored signing secret of the ACTIVE Outline Connection that
registered it. A forged id simply matches no connection, so the HMAC over the exact request bytes
fails. Every document event drives the same whole-workspace reconcile — Outline has no per-document
refresh API — so `OutlineSubjectParser` collapses the flat event space onto one logical handler key
(`EventTypeKey(OUTLINE, "document")`) rather than registering identical per-event handlers, while the
specific event still rides the subject and dedup key for observability. The periodic sync remains the
source of truth; a webhook only improves freshness between reconciles.

**Signing-secret storage — encrypted at rest.** The per-subscription HMAC secret is a server-generated
256-bit random key, encrypted with AES-256-GCM (`EncryptedStringConverter`) before it is stored in the
Connection's `config` JSONB (`OutlineConfig.webhookSecret`), so the ciphertext sits next to the
subscription id it is keyed by in one atomically-updated record. The registrar encrypts before storing and
the subscription-scoped secret source decrypts at verification time; the plaintext exists only in memory
for the length of a delivery's HMAC check and is never logged. This matches the API token's at-rest posture
(both rely on `hephaestus.security.encryption-key`), so an operator with raw DB read access sees only
ciphertext. Outline is the first integration with a *per-tenant* webhook secret — GitLab and Slack verify
against one app-global `hephaestus.webhook.secret` property, so they never stored per-Connection signing
material — which is why the secret rides its own encrypted field rather than the credential bundle
(`credentials_encrypted`), which models only the single API token. Rotation is a de-register/re-register
of the subscription.

**Multi-stream consumer fleet.** A workspace may now bind to more than one JetStream stream — its SCM
stream (`github`/`gitlab`) for repository/organization events and, independently, the `outline` stream
for documentation change notifications. `NatsSubscriptionProvider.NatsSubscriptionInfo` therefore
carries a `List<StreamSubscription>` (one `{streamName, subjects}` per stream) instead of a single
stream name, and the consumer fleet creates one durable consumer per `(scope, stream)` with a
stream-suffixed durable name (`…-scope-<id>-outline`) so SCM and Outline durables never collide. An
Outline-only workspace binds solely to the `outline` stream — it is no longer mislabeled onto the
`github` stream by the old single-stream fallback.

### 4. SSRF posture is reuse, not new work

The admin-supplied server URL is validated with `ServerUrlValidator` and every request is issued
through the SSRF-guarded WebClient connector (`WebClientConnectors.ssrfGuarded()`, which closes the
DNS-rebind bypass). This is the same stack `GitLabPreflightService` uses for the identical
admin-supplied-host threat model. Recorded here so no future reviewer re-implements an Outline-specific
guard: the SSRF defense is a reuse of existing components, not bespoke code.

### 5. ADR 0020 reconciliation — Connector-superset promotion deferred

[ADR 0020](0020-context-fabric-everything-is-an-integration.md) §3 nominated Outline to trigger the
promotion of the content-source seam into a single `Connector` superset SPI. Outline instead uses the
shipped `ContentSource` seam plus the agent-owned `DocumentProjection` inversion, and consciously
**defers** that promotion: Slack already set the ad-hoc precedent for a non-SCM content source, and
the superset promotion is a separate cross-cutting refactor that this integration does not need to
carry. This closes the open consistency question rather than reopening it.

### 6. Privacy divergence from messaging ingestion

Outline docs are deliberately-published, team-authored artifacts read by an admin-authorized bot
against an explicit collection allow-list — legally distinct from ingesting private live
conversation. Consequently:

- **No per-person opt-out.** A document author cannot opt a shared ADR out of the team knowledge
  base; consent is the admin authorization plus the allow-list plus the connection audit trail.
- **No in-product announcement.** That would require a write scope the integration deliberately does
  not hold. The admin allow-list and audit record are the disclosure.
- **No body encryption at rest.** Only the API token is encrypted (per-row AES-GCM, ADR 0014).
  Document bodies are governed by retention and erasure, the same class as any mirrored content;
  app-encrypting a churning cache would break size accounting and debuggability.
- **Erasure parity is retained.** A workspace purge cascades into a bulk delete of the mirrored
  documents via a purge contributor ordered ahead of the connection teardown, and every sync cycle
  tombstones documents that vanished upstream.

### 7. Retention driver is upstream freshness plus a size cap, not a wall clock

A document that still exists upstream is live, admin-authorized reference material; deleting it on an
age clock only forces a re-sync. Retention is therefore driven by reconciling to upstream (tombstone
on delete) and a per-workspace least-recently-materialized size cap, with a hard staleness ceiling on
tombstoned rows as defense-in-depth. This is a deliberate divergence from the wall-clock retention the
messaging integration runs.

## Consequences

- A documentation vendor now extends the framework with a family, a kind, a config subtype, a content
  source, and an agent projection — no schema change to detection or feedback.
- The webhook is authenticated by a per-subscription HMAC resolved in process; the generic NATS lane
  is untouched and no `OUTLINE` stream exists.
- The SSRF surface is covered by existing, tested guards.
- The next documentation vendor (Confluence, Notion) reuses the family, the mirror shape, the
  projection seam, and this privacy and retention posture.
