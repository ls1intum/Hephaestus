# Practice catalog curation

The practice catalog turns observable engineering habits into review criteria. Product terminology
lives in the [practice feedback language guide](practice-feedback-language.md); this page covers how
to maintain the catalog.

## Sources and ownership

The effective catalog combines three scopes:

| Scope | Source | Owner | Effect |
| --- | --- | --- | --- |
| Hephaestus defaults | `default-catalog.json` and its precompute scripts | repository maintainers | provides bundled definitions and order |
| Instance catalog | bundled defaults plus sparse database overrides | instance administrators | defines what new workspaces receive |
| Workspace practices | independent database copies | workspace administrators | defines reviews in one workspace |

The instance tables store only decisions that differ from the bundled catalog: a customized
definition, inclusion policy, accepted bundled digest, or position. No override row means the
bundled definition and order apply. See
[ADR 0028](https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0028-source-synced-practice-catalog.md)
for the architectural decision.

## Durable invariants

- **Slug is identity.** It participates in provenance and finding recurrence. Change the display name
  freely; rename a slug only with an explicit remapping strategy.
- **Definitions and order are independent.** Reordering does not create a definition override or an
  audit event. **Use Hephaestus order** removes custom positions.
- **Workspace installation happens once.** Later instance changes never rewrite workspace copies.
- **Provenance is descriptive, not referential.** Matched workspace copies retain the source slug and
  comparison fingerprint without a foreign key. A bundled source may have no database row and may
  disappear in a later release.
- **Unmatched migrated copies remain unlinked.** Backfill links only definitions that still match the
  bundled review rules or area details.
- **Exclusion affects future workspaces only.** Excluding an area also excludes its practices from new
  installations; existing workspaces do not change.

Array order in `default-catalog.json` is the bundled order. Until an administrator reorders a list,
bundled entries continue to follow repository order and instance-created entries append. The first
deliberate reorder records the complete affected list. Moving a practice to another area is a
definition change and is audited.

## Release behavior

Each effective entry is resolved from the running bundled definition and any instance override:

| Existing instance state | Effective result after upgrade | Admin-page state |
| --- | --- | --- |
| Default not customized | new bundled definition | no badge |
| Customized; bundled definition unchanged | saved customization | **Customized on this instance** |
| Customized; bundled definition changed | saved customization | **Hephaestus update available** |
| Instance-created entry | saved definition | **No Hephaestus default** |
| Uncustomized default removed | entry disappears | — |
| Customized default removed | saved customization | **Removed from Hephaestus defaults** |

An update never replaces a customization silently. Administrators can inspect the complete bundled
definition and whether applying it changes review rules, guidance, or area appearance. Applying an
update removes only the definition customization; inclusion policy and custom order remain. Keeping
the saved version records the bundled digest that was reviewed.

Git versions bundled defaults. Content-derived ETags reject concurrent writes based on stale content,
and the configuration audit records definition and inclusion changes. There is no separate catalog
revision counter or materialized copy of every bundled definition.

## Workspace drift

Catalog-installed or successfully matched workspace copies retain their source slug and the
comparison fingerprint captured at installation. The workspace UI derives drift by comparing:

1. the definition currently used by the workspace;
2. the definition copied originally; and
3. the current effective instance definition.

The ordinary matching state has no badge. Exceptions say **Customized for this workspace**,
**Instance catalog changed**, or **Not in the current instance catalog**. Drift is informational and
never rewrites the workspace.

A practice comparison covers the inputs that affect review behavior: slug, name, work type, trigger
events, criteria, evidence declaration, precompute script, and area. **Why it matters** and **What good looks like** are
guidance and do not affect review-rule drift. An area comparison covers name, description, icon, and
color; position is excluded.

## Selecting a practice

A practice must:

1. describe an observable habit within supported reviewed work;
2. distinguish evidence from inference and avoid claims that the code is correct;
3. define applicability and the evidence required to report it;
4. define false-positive exclusions and proportionate severity;
5. use language useful to the developer doing the work; and
6. cite research, a standard, or an explicitly identified practitioner norm.

Evidence requirements use the versioned
[artifact-source contract](./artifact-source-contract). They declare logical required/optional sources, minimum
completeness/freshness, conservative refusal, and known blind spots. They must not infer availability
from a missing file or require desired content to be non-empty. An author declaration is not independent
observability validation.

The detection fingerprint uses an explicit scheme prefix. Evidence-aware definitions use `v2`; stored `v1`
fingerprints retain their original pre-contract meaning and never compare equal by accident.

Architecture-wide qualities cannot be inferred from one change. A practice may review an observable
act, such as recording a decision, without turning it into a claim about the whole system.

Classify evidence accurately:

| Classification | Requirement |
| --- | --- |
| Peer-reviewed evidence | an empirical study directly supports the claimed relationship |
| Standard or canonical guidance | a recognized standard or established engineering guide recommends it |
| Practitioner norm | a community convention with no controlled-outcome claim |

Record exact sources in the proposal or pull request that changes the practice. Do not present a
standard as an experiment or a convention as a proven outcome.

## Changing bundled defaults

1. State the user problem and supported reviewed work.
2. Cite and classify the evidence.
3. Draft applicability, signals, exclusions, evidence requirements, and severity.
4. Validate every source against the applicable evidence profile and confirm its governance decision permits the product
   purpose, audience, processor egress, and retention. A new source follows the
   [artifact-source governance gate](../admin/dsms/artifact-source-governance).
5. Update `server/src/main/resources/practices/default-catalog.json`; its Git history is the bundled
   version history.
6. Add or update focused detection tests, including required-source refusal and valid-empty evidence.
7. Review the admin presentation and a representative delivered message.

Create workspace-specific practices through the admin UI or API so validation, ordering, revisions,
and audit behavior remain intact.
