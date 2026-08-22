# Architecture Decision Records

Decisions that shape the Hephaestus server foundations. Each ADR follows the
[MADR](https://adr.github.io/madr/) template: context → drivers → considered options
→ decision → consequences → revisit trigger.

New ADRs use the next available number and link from this index.

## These are repo-only — deliberately not on the docs site

`docs/decisions/`, `docs/runbooks/`, `docs/auth-architecture.md` and `docs/auth-glossary.md` are
**not** registered with any Docusaurus plugin and never appear on
[the site](https://ls1intum.github.io/Hephaestus/). That is a decision, not an oversight. They are
written against internal class names, they are amended by the same PR that changes the code, and
their reader already has the repository checked out. This index is their table of contents.

Two rules follow, and CI enforces the second one:

1. **An ADR is a record of a decision, not a changelog.** Context, drivers, options, decision,
   consequences, revisit trigger. A bug fixed after the fact belongs in a changeset
   (`.changeset/*.md`), not in an amendment — amend an ADR only when the decision or one of its
   consequences actually changed.

   **An amendment appends; it never rewrites.** Add a dated `## Update — YYYY-MM-DD (issue #N)`
   block at the end, say which section above it supersedes, and leave that section standing. This
   applies hardest to the considered-and-rejected options: they are the reason a future engineer
   does not re-propose a path that was already priced and declined, and deleting them is what turns
   an ADR into the changelog rule 1 forbids. If a previously rejected option is now the chosen path,
   the update block says so and dates it. [ADR 0005](0005-two-role-runtime-via-conditional-on-property.md)
   is the worked example.
2. **A published page links here by absolute GitHub URL, never a relative path.** A relative
   `../decisions/…` link from `docs/user/`, `docs/contributor/` or `docs/admin/` resolves to
   nothing on the site, and `onBrokenMarkdownLinks: 'throw'` fails the docs build. Use
   `https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/<file>.md`. Relative links
   *between* files in this tree are fine — they are only ever read on GitHub.

An operator-facing fact must not live only here. If a runbook step is something a self-hoster has to
do, its home is the Admin Guide (`docs/admin/`) and the runbook links to it.

| # | Title | Status |
|---|---|---|
| [0001](0001-flat-top-level-layout.md) | Flat top-level layout | Accepted |
| [0002](0002-java-package-rename-to-aet-cit-tum.md) | Rename Java base package to `de.tum.cit.aet.hephaestus` | Accepted |
| [0003](0003-spring-modulith-adoption.md) | Spring Modulith 2.0 adoption with pragmatic shared kernels | Accepted |
| [0004](0004-sql-layer-tenancy-via-statement-inspector.md) | SQL-layer tenancy enforcement via WorkspaceStatementInspector | Accepted |
| [0005](0005-two-role-runtime-via-conditional-on-property.md) | Two-role runtime topology via `@ConditionalOnProperty` | Accepted |
| [0006](0006-llm-proxy-on-coordinator-trust-model.md) | LLM proxy stays on the coordinator (BYO trust model); extended to the server-side OpenAI-compatible model catalog | Accepted (amended 2026-07-20 #1368, 2026-07-28 #1400) |
| [0007](0007-sandbox-spi-shape.md) | Sandbox SPI shape — sealed VolumeMount + typed NetworkPolicy | Accepted |
| [0008](0008-webhook-runtime-role.md) | Webhook as a third runtime role (`webhook-server` container) | Accepted |
| [0009](0009-worker-runtime-substrate-wss-control-channel.md) | Worker runtime substrate over WSS control channel | Accepted |
| [0010](0010-outbound-oauth-state-handrolled.md) | Outbound OAuth state hand-rolled (HMAC-signed, no library) | Accepted |
| [0011](0011-integration-identity-not-wired-from-sync.md) | `integration_identity` is OAuth-fed, not sync-fed | Superseded by [0016](0016-unified-identity-keycloak-as-truth.md) |
| [0012](0012-cross-instance-identity-safety-on-sync-paths.md) | Cross-instance identity safety on sync paths | Accepted |
| [0013](0013-no-jetstream-dlq-stream.md) | No JetStream DLQ stream (in-place NAK with backoff) | Accepted |
| [0014](0014-per-row-aes-gcm-aad-binding.md) | Per-row AES-GCM AAD binding for credential storage | Accepted |
| [0015](0015-unified-integration-framework.md) | Unified integration framework — single SPI for SCM/messaging vendors | Accepted (amended 2026-05-27 for Phase 1-4 restructure) |
| [0016](0016-unified-identity-keycloak-as-truth.md) | Unified identity — Keycloak `sub` as the authoritative join key | Accepted |
| [0017](0017-replace-keycloak-with-spring-native-auth.md) | Replace Keycloak with Spring-native auth (BFF cookie-JWT + `Connection`-backed workspace IdPs) | Accepted |
| [0018](0018-pg-partman-for-auth-event-partitioning.md) | `pg_partman` for `auth_event` partitioning | Accepted |
| [0019](0019-workspace-membership-keyed-on-account.md) | Workspace membership is keyed on `Account`, not the SCM `User` | Proposed |
| [0020](0020-context-fabric-everything-is-an-integration.md) | Context Fabric: everything is an integration | Accepted (amended 2026-08-04 #1430) |
| [0021](0021-observations-feedback-synthesis-seam.md) | Findings vs feedback — evidence and delivered feedback are separate records | Accepted (amended 2026-07-31 #1423, 2026-08-16 #1430) |
| [0022](0022-observation-presence-assessment-and-schema-cleanup.md) | Observation = presence × assessment (drop `Practice.kind`); reaction anchors on feedback; ruthless column cleanup | Accepted |
| [0023](0023-outline-documentation-integration.md) | Outline documentation integration — a content source, not a detection surface | Accepted |
| [0024](0024-integration-sync-lifecycle-and-two-deletion-semantics.md) | Integration sync lifecycle — drift tombstones and mirror erasure are two different operations | Accepted |
| [0025](0025-agent-job-queue-on-postgresql.md) | Agent job queue moves off NATS onto PostgreSQL | Accepted |
| [0026](0026-per-purpose-agent-bindings-and-llm-governance.md) | Per-purpose agent bindings and governed OpenAI-compatible LLM catalog | Accepted (amended 2026-07-26 — named-agent-config model deleted) |
| [0027](0027-dialog-lifetime-and-where-a-write-outcome-lands.md) | Dialog lifetime, and where a write's outcome lands when the dialog is gone | Accepted |
| [0028](0028-source-synced-practice-catalog.md) | Source-synced practice catalog with sparse instance overrides | Accepted |
| [0029](0029-measurement-intervention-seam-and-channel-levels.md) | Measurement and intervention are separate turns; a channel names where feedback lands, and its level follows | Accepted |
| [0030](0030-agent-runtime-is-typescript-on-bun.md) | The agent runtime is TypeScript on Bun, with no Node in the sandbox | Accepted |

Template: [0000-template.md](0000-template.md).
