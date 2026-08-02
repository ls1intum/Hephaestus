# Practice catalog curation

The practice catalog turns observable engineering habits into review criteria.

## Where a definition lives

A definition exists in three places, and each one owns a different decision.

| Layer               | Owner             | Changes how                                 |
| ------------------- | ----------------- | ------------------------------------------- |
| Hephaestus defaults | this repository   | a pull request                              |
| Instance catalog    | an instance admin | `/admin/catalog`, or a newer build arriving |
| Workspace practices | a workspace admin | the workspace's own practice administration |

The middle layer is not a copy of the first. What an instance offers is computed on every read as the
shipped catalog with the administrator's overrides laid over it: an entry nobody has touched has no
row at all and follows Hephaestus by saying nothing about it, and an entry an administrator wrote or
edited is stored once, alongside the digest of the shipped definition they started from. There is no
merged table to keep in step with its inputs and nothing that has to run when a build changes — a
deployment changes what the instance offers the moment it starts.

Areas and practices are the same kind of thing at every layer. Both carry a durable slug, the same
override shape, and retirement; both are administered with the same operations. Where they differ is
what a change means: an area's definition is how it presents, a practice's is what it detects.

The shipped catalog is
[`default-catalog.json`](https://github.com/ls1intum/Hephaestus/blob/main/server/src/main/resources/practices/default-catalog.json)
plus the precompute scripts beside it. Everything else is database state, projected through the
generated OpenAPI.

Array order in that file is the shipped order. Order is presentation metadata, not part of an area
or practice definition: an instance administrator can reorder with drag and drop, the keyboard, or
the row actions without creating a content override or a configuration-audit entry. Until an
administrator reorders anything, bundled entries continue to follow the repository order and custom
entries append without freezing it. The first deliberate reorder records the complete order of the
affected list. **Use Hephaestus order** clears all recorded positions and resumes following the
repository order. Moving a practice between areas is a content change because its placement affects
the definition and is therefore audited.

## Selection principles

A practice must:

1. describe an observable habit within supported reviewed work;
2. distinguish evidence from inference and avoid claiming that the code is correct;
3. specify when it is applicable and the evidence needed to report it;
4. define false-positive exclusions and proportionate severity;
5. use language that is useful to the person doing the work;
6. have credible grounding in research, a standard, or an explicitly identified practitioner norm.

Architecture-wide qualities are not inferred from one change. A practice may review an observable
act such as recording a decision, but it must not turn that act into a claim about the whole system.

## Evidence claims

Use the strongest accurate description:

| Label                          | Meaning                                                                        |
| ------------------------------ | ------------------------------------------------------------------------------ |
| Peer-reviewed evidence         | An empirical study directly supports the claimed relationship                  |
| Standard or canonical guidance | A recognized standard or established engineering guide recommends the practice |
| Practitioner norm              | A community convention; no controlled outcome claim is made                    |

Do not present a standard as an experiment or a convention as a proven outcome. Record the exact
source in the proposal or pull request that changes the catalog so reviewers can assess the claim.

## Identity and wording

Administrator-facing copy uses this glossary:

| Term | Meaning |
| ---- | ------- |
| **Hephaestus default** | A definition included with the running Hephaestus release |
| **Instance catalog** | The starting set copied into each new workspace |
| **Workspace practices** | Independent definitions used for reviews in one workspace |
| **Review rules** | The inputs and criteria that determine review behavior |
| **Developer guidance** | Explanatory text that does not change review behavior |
| **Customize** | Change a default or catalog-based definition |
| **Include / exclude** | Whether an instance entry is copied into new workspaces |
| **No Hephaestus default** | An entry maintained by the instance, which may still be included in new workspaces |

UI copy does not use _shipped_, _offered_, _retired_, _ours_, _yours_, or _here_ for these concepts.
Those terms expose implementation or depend on who is reading. Workspace review participation uses
_used in new reviews_ so it cannot be confused with inclusion in new workspaces.

The slug is durable identity and participates in finding recurrence. Once findings exist, renaming a
slug requires an explicit remapping strategy; changing only the display name is not a remap.

Instance-authored slugs share one namespace with the shipped ones. If a later build ships a slug an
instance already used, that entry starts tracking the shipped definition rather than hiding it: the
instance's own definition stays in force and the shipped one arrives as an update to take or leave.
Prefixing instance-only slugs keeps that from happening by accident.

Learner-facing fields should be plain and specific:

- **Name:** a short action or outcome.
- **Why it matters:** the failure the habit prevents and the practical benefit.
- **What good looks like:** one concrete, technology-neutral end state.
- **Criteria:** observable signals, exclusions, evidence requirements, and severity boundaries.

Avoid hype, grading language, and claims broader than the artifact being reviewed.

## Changing the shipped catalog

1. State the user problem and supported artifact.
2. Cite the evidence and classify the claim honestly.
3. Draft applicability, positive/negative signals, exclusions, and severity.
4. Change `default-catalog.json`. There is no revision number to bump: the file _is_ the shipped
   layer, so every instance running the new build offers the new definition as soon as it starts.
5. Add or update focused detection tests when the execution contract changes.
6. Review learner-facing copy in the admin UI and a representative delivered message.

## What a newer build does to an instance

The catalog resolves each entry from the running Hephaestus default and any instance customization:

| The administrator has…                    | The instance uses     | The admin page shows                  |
| ----------------------------------------- | --------------------- | ------------------------------------- |
| not customized the entry                   | the new default      | no badge; nothing needs attention    |
| customized it; its default has not changed | the customization     | **Customized on this instance**       |
| customized it; its default has changed    | the customization     | **Hephaestus update available**       |
| created or retained an entry with no default | the saved definition | **No Hephaestus default**            |
| not customized an entry the build removes | no catalog entry      | the entry disappears                  |
| customized an entry the build removes     | the customization     | **Removed from Hephaestus defaults**  |

An update never replaces a customization silently. The page says whether it changes review behavior,
wording or guidance, or area appearance. The updated default can be read before it is applied.

Applying an update removes the definition customization, so the entry uses the Hephaestus default
again; exclusion and local ordering are preserved. Keeping the customization records that the
administrator reviewed this default, so the same update is not raised twice.

The catalog has no separate revision counter. Git history versions the Hephaestus defaults; full
content digests provide conditional-write ETags; and the configuration audit trail timestamps every
administrator decision. A materialized revision table would duplicate the definitions already owned
by the repository and the sparse overrides without adding a decision the product needs to expose.

Exclusion is instance policy: it stops an entry being copied into new workspaces and changes nothing
that already exists. Excluding an area also excludes the practices in it; the confirmation names
those practices before the change.

## What a workspace receives

A workspace is given the instance catalog once, when it is created — every entry the instance
includes, whether Hephaestus provided it or an administrator wrote it. From that moment the workspace's
practices are its own. Later catalog changes never rewrite them.

That is deliberate, and it is visible rather than silent. Each workspace practice and area records
the slug it came from and the fingerprint it was copied at — two columns, no foreign key into the
catalog, because a workspace's practice must survive its source being retired. Comparing three
fingerprints (what is running here, what it was copied from, what the instance offers now) gives the workspace practice administration what it shows. The ordinary matching state has no badge.
Exceptions say **Customized for this workspace**, **Instance catalog changed**, or **Not in the
current instance catalog**. A catalog change does not promise an update action;
the mechanism for adopting it is separate work.

Equivalence is derived from the definition, not from the order edits happened in. A practice's
review-rule fingerprint includes its identifier, name, work type, trigger events, evaluation criteria,
precompute script, and area. Developer guidance is excluded, so changing only why the practice matters
or what good looks like does not change its review-rule status. For an area, the comparison covers its
name, description, icon, and color but excludes position because ordering is a layout choice rather
than a definition edit.

Workspace-specific practices should be created through the admin UI or API, not direct SQL, so
validation, ordering, revisions, and audit behaviour remain intact.

## Reference register

These sources are useful starting points, not automatic justification for any particular practice:

- [Google Engineering Practices](https://google.github.io/eng-practices/review/)
- [DORA capabilities](https://dora.dev/capabilities/)
- [Guide to the Software Engineering Body of Knowledge (SWEBOK)](https://www.computer.org/education/bodies-of-knowledge/software-engineering)
- [ISO/IEC 25010 systems and software quality models](https://www.iso.org/standard/78176.html)
- [OWASP Application Security Verification Standard](https://asvs.dev/)
- [OWASP Top 10](https://owasp.org/Top10/)
- Bacchelli and Bird,
  [Expectations, Outcomes, and Challenges of Modern Code Review](https://sback.it/publications/icse2013.pdf)
- Sadowski et al.,
  [Modern Code Review: A Case Study at Google](https://sback.it/publications/icse2018seip.pdf)
- Bettenburg et al.,
  [What Makes a Good Bug Report?](https://thomas-zimmermann.com/publications/files/bettenburg-fse-2008.pdf)
- Hattie and Timperley,
  [The Power of Feedback](https://journals.sagepub.com/doi/10.3102/003465430298487)
- [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)
- [OpenSSF Scorecard](https://scorecard.dev/) and [SLSA](https://slsa.dev/)
