# ADR 0020: Context Fabric for agent evidence

**Status:** Accepted

**Date:** 2026-06-12

**Updated:** 2026-08-03

## Context

Practice detection reads projections of external systems: pull requests, issues, discussions, repository trees,
Slack threads, and Outline documents. Treating those inputs as anonymous files makes an empty result ambiguous and
prevents an operator from reconstructing what a model was allowed to use.

The runtime therefore needs one evidence boundary independent of the upstream integration. PostgreSQL remains the
system of record; filesystem projections are bounded, rebuildable cache entries.

## Decision

### Versioned source contract

Every input that may support a practice-detection judgment has a logical source kind in the versioned artifact-source
catalog. The catalog defines authority, capture time, freshness, completeness, privacy class, supported missingness,
purpose, retention, erasure, and its governance decision. Evidence profiles close the set of source kinds available
to each reviewed artifact type.

A practice revision declares required and optional source kinds. The runtime records a full source manifest and a
per-practice readiness report. If required evidence is unavailable, stale, incomplete, redacted, or denied, that
practice receives an `INSUFFICIENT_EVIDENCE` readiness outcome; the detector does not manufacture a semantic
`NOT_APPLICABLE` result.

The contract is scoped to practice detection. Mentor conversation context is governed by the mentor consent and
integration contracts and cannot be cited as practice-detection evidence. If a mentor input is reused for detection,
it must first become a catalogued source kind. Contract tests reject any uncatalogued `EvidenceSource` provider.

### Filesystem layout

`FabricLayout` owns the cache root:

```text
sources/scm/{repositoryId}/     local integration cache
cas/sha256/{ab}/{rest}          immutable content-addressed blobs
jobs/{jobId}/                   bounded replay manifest and readiness report
```

The sandbox sees only materialized files beneath `/workspace/inputs`; handlers do not add host mounts. A repository
tree, when explicitly selected by an evidence plan, is copied into `inputs/sources/scm/repo/` and inventoried like
any other source. The model-visible manifest contains the selected artifact index. The complete source manifest and
readiness report are persisted in the job evidence snapshot for audit after replay-cache collection.

CAS writes and collection use cross-process prefix locks. Collection removes only blobs that are unreferenced and
older than the retention cutoff. Reusing a blob refreshes its retention age before a new manifest is published.
Unreadable manifests stop the sweep rather than risking deletion of live evidence.

### Governance and minimization

A checked-in, unexpired engineering decision is necessary but not sufficient to collect a source. Each deployment
also has a default-empty `hephaestus.evidence.authorized-source-kinds` allowlist controlled by its data controller.
Both capture and delivery check the gate. Removing a kind prevents new collection and prevents delayed delivery from
using a withdrawn source.

Adding or widening a source requires an updated source descriptor, profile inventory, governance decision,
retention/erasure coverage, and contract tests. Accuracy benefit alone is not authorization. Published contract
versions are immutable; renewal or semantic change creates a new version.

### Provenance and delivery

Artifacts are addressed by SHA-256 and citations bind a finding to a declared, available source and exact artifact.
Diff citations additionally bind file, side, and line range. Deterministic secret scanning validates a transient line
digest but removes that digest before persistence, retaining only redacted location evidence.

This proves source attribution, not causal isolation inside a shared model invocation. The runtime still rejects any
finding that cites a source outside its practice declaration.

## Consequences

- Empty, missing, stale, partial, redacted, failed, and ablated inputs are distinguishable.
- Retained job records preserve the exact contract, manifest, and readiness outcome for authorized operator audits;
  this ADR does not add a dedicated audit UI.
- With no deployment authorization, practice detection declines rather than collecting data.
- Source-contract changes require explicit version migration and mark dependent claims stale.
- The CAS and replay cache have bounded residual windows; immediate selective erasure requires reference-aware
  collection and remains a deployment approval consideration.

## Evidence

- [W3C PROV-DM](https://www.w3.org/TR/prov-dm/) for explicit entities, activities, and provenance.
- [SLSA provenance v1.2](https://slsa.dev/spec/v1.2/provenance) for immutable subject identity.
- [JSON Schema 2020-12](https://json-schema.org/draft/2020-12/json-schema-core) for closed machine contracts.
- [GDPR Article 5](https://eur-lex.europa.eu/eli/reg/2016/679/art_5/oj) and
  [Article 25](https://eur-lex.europa.eu/eli/reg/2016/679/art_25/oj) for minimization, storage limitation, and
  protection by default.
