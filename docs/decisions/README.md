# Architecture Decision Records

Decisions that shape the Hephaestus server foundations. Each ADR follows the
[MADR](https://adr.github.io/madr/) template: context → drivers → considered options
→ decision → consequences → revisit trigger.

New ADRs use the next available number and link from this index.

| # | Title | Status |
|---|---|---|
| [0001](0001-flat-top-level-layout.md) | Flat top-level layout for Java + TypeScript deployables | Accepted |
| [0002](0002-java-package-rename-to-aet-cit-tum.md) | Rename Java base package to `de.tum.cit.aet.hephaestus` | Accepted |
| [0003](0003-spring-modulith-adoption.md) | Spring Modulith 2.0 adoption with pragmatic shared kernels | Accepted |
| [0004](0004-sql-layer-tenancy-via-statement-inspector.md) | SQL-layer tenancy enforcement via WorkspaceStatementInspector | Accepted |
| [0005](0005-two-role-runtime-via-conditional-on-property.md) | Two-role runtime topology via `@ConditionalOnProperty` | Accepted |
| [0006](0006-llm-proxy-on-coordinator-trust-model.md) | LLM proxy stays on the coordinator (BYO trust model) | Accepted |
| [0007](0007-sandbox-spi-shape.md) | Sandbox SPI shape — sealed VolumeMount + typed NetworkPolicy | Accepted |

Template: [0000-template.md](0000-template.md).
