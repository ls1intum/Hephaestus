# Practice catalog curation

The practice catalog turns defined engineering practices into review criteria. Product terminology
lives in the [practice feedback language guide](practice-feedback-language.md); this page covers how
to maintain the catalog.

## Sources and ownership

The effective catalog combines three scopes:

| Scope | Source | Owner | Effect |
| --- | --- | --- | --- |
| Hephaestus defaults | `default-catalog.json` and its precompute scripts | repository maintainers | provides bundled definitions and order |
| Instance catalog | bundled defaults plus sparse database overrides | instance administrators | defines what new workspaces receive |
| Workspace practices | independent database copies | workspace administrators | defines reviews in one workspace |

The same definition fields are used at all three scopes. What changes is who owns the value and when
it propagates:

| Decision | Hephaestus defaults | Instance catalog | Workspace practices |
| --- | --- | --- | --- |
| Name, work type, criteria, and guidance | maintained in the repository | inherited or customized by an instance administrator | copied at workspace creation, then owned by the workspace |
| AI-supported mentoring and required evidence | selected by `automatedReviewPolicyId`; optional precompute input is explicit | inherited or customized in the practice form | copied, then customizable in the same practice form |
| Included in new workspaces | default is included | instance administrator can include or exclude | not applicable after installation |
| Used in new reviews | not a repository setting | not a curated-catalog setting | workspace administrator controls it; unavailable automation cannot be enabled |
| Area and order | JSON array order | inherited or changed with drag-and-drop or move actions | copied, then independently managed |

This is a one-way lifecycle: a repository update can update an uncustomized instance definition, and
an instance definition can seed a new workspace. Neither step silently rewrites a customized instance
definition or an existing workspace.

| Stakeholder | Primary task | Deliberately not their task |
| --- | --- | --- |
| Practice author | Define the habit, guidance, and responsible mentoring support | Authorize collection or certify review accuracy |
| Instance administrator | Curate the defaults offered to new workspaces | Rewrite existing workspace practices |
| Workspace administrator | Adapt practices and choose which are used in new reviews | Authorize a new data source for the instance |
| Instance operator | Approve source purposes, privacy, retention, and erasure coverage | Decide that connected evidence proves a practice |
| Developer, peer, or mentor | Use findings and available human context in a review | Supply hidden context to Hephaestus implicitly |

## Authoring experience

The practice editor follows the decisions an author can make confidently:

1. **Practice** — give one observable habit a short, action-oriented name, choose the work it
   applies to, and optionally group it in an area.
2. **Review guidance** — describe what to look for, why it matters, and one concrete example.
3. **How Hephaestus can help** — choose AI-supported mentoring, human review, or guidance only.

The generated identifier, review timing, and optional static-analysis script are under **Technical
settings**. New practices start with review events that fit the selected work type. Authors only
change those defaults when the practice genuinely needs different timing. This keeps runtime
plumbing out of the common path without hiding it from expert authors.
The definition-options API supplies both available and recommended events, so the editor and runtime
cannot silently disagree about valid review timing.

Write **What to look for** as a review boundary, not as a personality or a score. Define one
observable habit, the signals that demonstrate it, and the cases where a reviewer should stay
silent. Do not require intent, private context, runtime behavior, or any other fact outside the
selected work and evidence boundary.

## Choose how Hephaestus can help

The practice form starts with one product choice instead of separate model and evidence-sufficiency
settings:

- **AI-supported mentoring** lets Hephaestus review connected work after every required source passes.
- **Human review needed** records that connected work is not enough. Hephaestus skips the practice,
  while a developer, peer, or mentor may still review it from context the system does not collect.
  Because Hephaestus will not run a review, this choice has no review events or static-analysis script.
- **Guidance only** keeps the criteria and guidance without configuring Hephaestus to review it.

Each reviewed-work type starts with recommended evidence. Most authors should keep it. **Customize
evidence** reveals source roles, minimum completeness and freshness, privacy classes, and known
limitations for practices that genuinely need a different rule. Selecting a source never authorizes
collection; instance governance and workspace integrations remain separate gates.

### Example: explain what changed and why

| Field | Definition |
| --- | --- |
| Name | Explain what changed and why |
| Review this kind of work | Pull or merge request |
| What to look for | Look for a description that explains the behavior change and why. Stay silent for automated dependency updates. |
| Why it matters | Reviewers can judge a change faster when they understand its purpose. |
| What good looks like | “This changes retry behavior so temporary network failures no longer end the sync.” |
| Hephaestus support | AI-supported mentoring with the recommended review timing and evidence |

The author does not choose source-contract identifiers or runtime states in this common path. If the
required pull-request details or diff are missing, incomplete, or outdated, Hephaestus skips the
practice instead of inventing a finding. A more contextual practice, such as whether a developer
understood a trade-off discussed privately with a mentor, should use **Human review needed** and name
that missing context.

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
events, criteria, evidence requirements, precompute script, and area. **Why it matters** and **What good looks like** are
guidance and do not affect review-rule drift. An area comparison covers name, description, icon, and
color; position is excluded.

## Selecting a practice

A practice must:

1. identify who is expected to review the habit and from which context;
2. distinguish evidence from inference and avoid claims that the code is correct;
3. define applicability and the evidence required to report it;
4. define false-positive exclusions and proportionate severity;
5. use language useful to the developer doing the work; and
6. cite research, a standard, or an explicitly identified practitioner norm.

Evidence requirements use the versioned
[artifact-source contract](./artifact-source-contract) and the canonical
[practice review glossary](./practice-review-glossary.mdx). They define required evidence, optional context,
minimum completeness and freshness, conservative skipping, and known limitations. They must not infer availability
from a missing file or require desired content to be non-empty. Author-defined requirements are not independent
validation of automated review. They say nothing about whether a developer, peer, or human mentor can review the practice
outside the governed integrations.

The review-rule fingerprint uses an explicit scheme prefix. Evidence-aware definitions use `v2`; stored `v1`
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
5. Update `server/src/main/resources/practices/default-catalog.json`; its adjacent JSON Schema provides
   editor completion and CI validation, and Git history is the bundled version history. Reference any
   precompute script explicitly; unreferenced scripts fail validation.
6. Add or update focused automated-review tests, including required-source skipping and valid-empty evidence.
7. Review the admin presentation and a representative delivered message.

Create workspace-specific practices through the admin UI or API so validation, ordering, revisions,
and audit behavior remain intact.
