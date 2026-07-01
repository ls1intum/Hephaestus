# Architecture Decision Records

Decisions that shape the Hephaestus server foundations. Each ADR follows the
[MADR](https://adr.github.io/madr/) template: context → drivers → considered options
→ decision → consequences → revisit trigger.

New ADRs use the next available number and link from this index.

| # | Title | Status |
|---|---|---|
| [0001](0001-flat-top-level-layout.md) | Flat top-level layout | Accepted |
| [0002](0002-java-package-rename-to-aet-cit-tum.md) | Rename Java base package to `de.tum.cit.aet.hephaestus` | Accepted |
| [0003](0003-spring-modulith-adoption.md) | Spring Modulith 2.0 adoption with pragmatic shared kernels | Accepted |
| [0004](0004-sql-layer-tenancy-via-statement-inspector.md) | SQL-layer tenancy enforcement via WorkspaceStatementInspector | Accepted |
| [0005](0005-two-role-runtime-via-conditional-on-property.md) | Two-role runtime topology via `@ConditionalOnProperty` | Accepted |
| [0006](0006-llm-proxy-on-coordinator-trust-model.md) | LLM proxy stays on the coordinator (BYO trust model) | Accepted |
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
| [0020](0020-context-fabric-everything-is-an-integration.md) | Context Fabric: everything is an integration | Proposed |
| [0021](0021-findings-feedback-synthesis-seam.md) | Findings vs feedback — detection produces evidence and in-context feedback; cross-channel synthesis is separate | Accepted |
| [0022](0022-observation-presence-assessment-and-schema-cleanup.md) | Observation = presence × assessment (drop `Practice.kind`); reaction anchors on feedback; ruthless column cleanup | Accepted |

Template: [0000-template.md](0000-template.md).
