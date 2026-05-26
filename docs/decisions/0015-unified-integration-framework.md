# ADR 0015: Unified integration framework — package layout and SPI surface

**Status:** Accepted
**Date:** 2026-05-26
**Authors:** Felix T.J. Dietrich

## Context

Before #1198, each vendor (GitHub, GitLab, Slack-as-notification) had its own
hand-rolled adapter, its own webhook route, its own message-handler registry,
and its own credential storage path on the `Workspace` row. Three external
modules (`workspace/`, `contributors/`, `gitprovider/common/github/app/`)
imported vendor classes directly. Adding Slack and Outline would have compounded
the linearity.

The cleanup landed in two phases: the iteration (passes 1–15) shaped the
contract surface; pass 16 cut over the runtime, consolidated the schema,
renamed the domain, and removed every backwards-compat shim. This ADR records
the surface the codebase now ships against so the next vendor doesn't have to
re-litigate it.

## Decision drivers

- New-vendor cost has to scale flat, not linearly.
- Vendor-specific vocabulary (`installation_id`, `whsec_*`, `xoxb-*`) cannot
  leak into the abstraction or into `Workspace`.
- Cross-module access must be enforceable by ArchUnit + Spring Modulith, not
  by convention.
- Credentials at rest must bind to row identity (AAD), not be substitutable
  across rows.
- The runtime cutover has to be atomic — partial states are worse than the
  pre-#1198 baseline.

## Considered options

1. **Status quo** — extend `gitprovider/` for each new family. Rejected:
   the package name says "git" and every consumer learns to mentally translate.
2. **Per-family roots** (`scm/`, `messaging/`, `knowledge/` as siblings,
   vendor under family). PE-B Alternative B. Rejected mid-epic: ~600 file
   moves, ~3000 import rewrites, and the cross-cutting `platform/` pile
   reintroduces what we were trying to eliminate.
3. **Pipeline-first** — collapse `webhook/`, `oauth/`, `consumer/`, `handler/`
   into one `ingest/`. PE-B Alternative A. Rejected: webhook (HMAC, raw
   body) and oauth (signed state, browser redirect) have different security
   models; grouping them obscures more than it reveals.
4. **Surgical fix on the current layout** — PE-B Alternative C, adopted.
   The current layout was 90% right; rename `registry/` → `connection/`,
   `manifest/` → `framework/`, move `GitProvider*` out of `scm/common/`,
   collapse the `sync/` orphan, fix one Javadoc lie. ~50 file moves.

## Decision

The integration domain lives under `integration/` with the following structure:

- `integration/spi/` — sole cross-module API surface (`@NamedInterface`).
- `integration/events/` — in-process `DomainEvent` family via Spring
  `ApplicationEventPublisher`. Wire-level publication is raw bytes plus
  vendor headers, in `integration/webhook/PublishRequest`.
- `integration/connection/` — `Connection` aggregate root, audit log, sealed
  `ConnectionConfig`, AES-256-GCM `CredentialBundleConverter`,
  `EncryptionContext` AAD. Also hosts the platform-level
  `GitProvider`/`GitProviderType` metadata (vendor identity, not domain).
- `integration/framework/` — `IntegrationFrameworkBootstrap`,
  `IntegrationManifestRegistry`, `WorkspaceCapabilityResolver`. Module
  startup + capability resolution.
- `integration/webhook/` — unified `WebhookController` (`POST /webhooks/{kind}`),
  ingest pipeline, JetStream publisher, dedup, payload size filter.
- `integration/consumer/` — NATS consumer + dispatcher + poison handler.
- `integration/handler/` — `IntegrationMessageHandler` SPI + `EventTypeKey`
  registry.
- `integration/oauth/` — outbound OAuth callback + signed-state nonce store
  (RFC 7636 PKCE-S256 + RFC 9700 single-use state).
- `integration/feedback/` — `FeedbackPost` table for edit-in-place feedback
  identity across vendors.
- `integration/identity/` — `HephaestusUser` + `IntegrationIdentity` (vendor
  account ↔ platform user binding).
- `integration/scm/` — the platform-agnostic SCM domain (formerly
  `gitprovider/`). Subpackages: `commit`, `issue`, `pullrequest`,
  `pullrequestreview`, `pullrequestreviewcomment`, `pullrequestreviewthread`,
  `label`, `milestone`, `team`, `user`, `repository`, `organization`,
  `discussion`, `discussioncomment`, `issuecomment`, `issuetype`, `project`,
  `sync`, `workdir` (filesystem clone manager, NOT git-domain), `common`.
- `integration/github/`, `integration/gitlab/`, `integration/slack/`,
  `integration/outline/` — vendor adapters, peers of `scm/`. Each carries
  its own `webhook/`, `lifecycle/`, `credentials/`, `manifest/`,
  `connect/` (OAuth/PAT setup) subpackages; GitHub adds `app/`,
  `installation/`. Slack and Outline are scaffolding for #1204/#1205/#1203.

`analytics/` is a sibling of `integration/`, not under it. PostHog is
product analytics, not a vendor integration. Naming the package after the
capability avoids the `integration/` vs `integrations/` foot-gun the
iteration accumulated.

The SPI surface is 23 interfaces across three axes:

- **Lifecycle** (5): `ProvisioningListener`, `IntegrationLifecycleListener`,
  `OrganizationMembershipListener`, `TeamMembershipListener`,
  `ConnectionStrategy`.
- **Wire** (8): `WebhookSignatureVerifier`, `WebhookSecretSource`,
  `SubjectKeyDeriver`, `SubjectParser`, `ApiCredentialProvider`,
  `TokenRefresher`, `InstallationTokenProvider`, `ScopeIdResolver`.
- **Capability** (10): `FeedbackChannel`, `InlineFindingChannel`,
  `ApprovalChannel`, `BackfillStateProvider`, `SyncContextProvider`,
  `SyncTargetProvider`, `SyncTimestampProvider`, `NatsSubscriptionProvider`,
  `RepositoryScopeFilter`, `IntegrationManifest`.

The 23:2 surface looks heavy — only GitHub and GitLab implement most of
these today; Slack/Outline are scaffolding. Each interface is single-purpose
and capability-gated: a vendor implements only what its `IntegrationManifest`
declares. This is intentional. The alternative (one omnibus `IntegrationAdapter`
interface with optional methods) reproduces the linear cost-per-vendor that
the epic was designed to eliminate. The next vendor pays only for the
capabilities it declares.

Credentials at rest use AES-256-GCM with AAD bound to
`(workspaceId, kind, instanceKey, columnFqn)`. The ciphertext envelope is
`[version-byte | 12-byte IV | ciphertext+16-byte GCM tag]`. Cross-row
substitution attacks fail because the AAD doesn't match.

The Liquibase migration is one file, `1779790459343_unified_integration_framework.xml`,
32 changesets. No data backfill — operators upgrading a live environment
must populate the `connection` table out-of-band before applying. This is
documented in the changelog header.

## Consequences

Positive:

- Adding the third SCM vendor (Bitbucket / Gitea / Forgejo) requires only
  `integration/<vendor>/` plus the SPI implementations the manifest declares.
  Zero changes elsewhere.
- ArchUnit pin tests in `IntegrationCutoverPinsTest` make regression
  expensive: re-introducing `..gitprovider..`, posting to legacy `/github`
  or `/gitlab` routes, or re-adding legacy `installationId`/`personalAccessToken`
  fields on `Workspace` all fail the build.
- Spring Modulith `ApplicationModules.of(...).verify()` (pinned by
  `ModulithVerificationTest`) catches cyclic dependencies and missing
  `@NamedInterface` grants.
- The Connection aggregate is the only authority for "which vendor is
  active for this workspace." There is no second source of truth.

Neutral:

- One-file changelog with 32 changesets is non-idiomatic; Liquibase
  guidance prefers one file per logical change. Defensible here because
  the file IS the unit of release for this epic — splitting it would
  spread one decision across 12 files for no operational benefit.
- The 23-SPI surface looks broad. It is — but each is small. The cost is
  conceptual surface area, not LOC.

Negative:

- The API contract break (`gitProviderMode` → `kind`) requires regenerating
  every client. Webapp regenerated in pass 16; future external API consumers
  must port at adoption time.
- No data backfill means a stale-DB environment loses the eleven legacy
  Workspace columns silently. Operator-facing, called out in the changelog
  header — not in a hidden code path.
- Slack and Outline ship as scaffolding (manifests, credential providers,
  OAuth callback, signature verifier). The Connection table accepts SLACK
  and OUTLINE rows; no business logic consumes them yet. This is intentional
  — #1203/#1204/#1205 fill in the business logic without re-litigating the
  SPI surface.

## Revisit trigger

- A new vendor whose conceptual model doesn't fit the three axes (lifecycle /
  wire / capability) — e.g. an event-source-only vendor with no webhook,
  no OAuth, no PAT, that pushes events via a third-party broker. If that
  shape arrives, the SPI axes need a fourth row, not a forced fit.
- A second cross-cutting trait that needs its own top-level package
  (`oauth/` already does; `webhook/` already does). If a third such trait
  appears (e.g. `outbound-http/` for vendor-side API calls that aren't
  webhooks or OAuth), the `integration/` root would gain a third sibling
  and the package layout should be re-evaluated.
- Cross-vendor subject linking (PR ↔ Outline doc, Slack thread ↔ MR) at
  scale — the current shape is per-vendor rows in `connection` plus
  per-subject rows in `feedback_post`. A scale-out would need a dedicated
  link table, not a generic graph.
