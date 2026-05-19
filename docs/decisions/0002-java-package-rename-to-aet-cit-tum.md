# ADR 0002: Rename Java base package to `de.tum.cit.aet.hephaestus`

**Status:** Accepted
**Date:** 2026-05-20
**Authors:** Server foundations epic (#1097)

## Context

The Java code under `server/src/main/java/` used `de.tum.in.www1.hephaestus` —
a legacy TUM naming convention referencing the "Informatics, chair www1" structure.
That chair structure has since been reorganized; the current owner is the AET
(Applied Education Technologies) chair at the CIT department, with research website
`aet.cit.tum.de` and prod domain `hephaestus.aet.cit.tum.de`.

The mismatch between the source-tree package name and the chair / prod-domain name
was a small but durable papercut: every new contributor asked about it; every PR
review touched lines that don't match the org any more.

## Decision drivers

- Vanity is real: prod domain alignment matters to the maintainers
- One-shot rename minimizes blast radius
- Modulith adoption is the right moment (we're already touching every package-info)

## Considered options

1. **Rename now to `de.tum.cit.aet.hephaestus`** — align with the current chair.
2. **Defer indefinitely** — accept the legacy name; nothing breaks technically.
3. **Rename to a generic name** like `com.hephaestus` — eliminates the dependency on TUM chair structure entirely.

## Decision

Adopt option 1. Rename `de.tum.in.www1.hephaestus` → `de.tum.cit.aet.hephaestus` across
all 1,121 Java source files in one mechanical commit.

## Consequences

- One large but isolated commit touches every Java file (every `package` and `import` line).
- Open feature branches will conflict; we coordinate the merge of the rename PR ahead of feature merges.
- Forks / external consumers (none today) would need to rebase — acceptable cost.
- Dynamic FQN references (Spring config `logging.level` keys, OpenAPI codegen targets, achievements-schema JSON `$ref`s) were swept in the same commit.
- `git log --follow` still tracks individual files across the rename.

## Revisit trigger

Next chair reorganization at TUM (would re-trigger the same papercut). Until then,
this is locked in.
