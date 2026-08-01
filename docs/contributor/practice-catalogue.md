# Practice catalogue curation

The practice catalogue turns observable engineering habits into review criteria.

## Where a definition lives

A definition exists in three places, and each one owns a different decision.

| Layer               | Owner             | Changes how                                 |
| ------------------- | ----------------- | ------------------------------------------- |
| Shipped catalogue   | this repository   | a pull request                              |
| Instance catalogue  | an instance admin | `/admin/catalog`, or a newer build arriving |
| Workspace catalogue | a workspace admin | the workspace's own practice administration |

The middle layer is not a copy of the first. What an instance offers is computed on every read as the
shipped catalogue with the administrator's overrides laid over it: an entry nobody has touched has no
row at all and follows Hephaestus by saying nothing about it, and an entry an administrator wrote or
edited is stored once, alongside the digest of the shipped definition they started from. There is no
merged table to keep in step with its inputs and nothing that has to run when a build changes — a
deployment changes what the instance offers the moment it starts.

Areas and practices are the same kind of thing at every layer. Both carry a durable slug, the same
override shape, and retirement; both are administered with the same operations. Where they differ is
what a change means: an area's definition is how it presents, a practice's is what it detects.

The shipped catalogue is
[`default-catalog.json`](https://github.com/ls1intum/Hephaestus/blob/main/server/src/main/resources/practices/default-catalog.json)
plus the precompute scripts beside it. Everything else is database state, projected through the
generated OpenAPI.

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
source in the proposal or pull request that changes the catalogue so reviewers can assess the claim.

## Identity and wording

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

## Changing the shipped catalogue

1. State the user problem and supported artifact.
2. Cite the evidence and classify the claim honestly.
3. Draft applicability, positive/negative signals, exclusions, and severity.
4. Change `default-catalog.json`. There is no revision number to bump: the file _is_ the shipped
   layer, so every instance running the new build offers the new definition as soon as it starts.
5. Add or update focused detection tests when the execution contract changes.
6. Review learner-facing copy in the admin UI and a representative delivered message.

## What a newer build does to an instance

Nothing runs, and nothing is rewritten. Each entry is resolved on read, so a build changes an
instance exactly as far as the administrator has left it alone:

| The administrator has…                       | The instance now offers | The admin page shows                     |
| -------------------------------------------- | ----------------------- | ---------------------------------------- |
| not touched the entry                         | the new definition      | **From Hephaestus** — nothing to decide  |
| edited it, and Hephaestus has not changed it  | their definition        | **Edited here**                          |
| edited it, and Hephaestus has now changed it  | their definition        | **Update waiting** — take it or keep ours |
| written the entry themselves                  | their definition        | **Yours**                                |
| an entry the build no longer ships            | their definition        | **No longer shipped**                    |

An update is never taken silently, and it is never presented as a number. The page says what the
change is — whether taking it would change what gets detected or only what people read — and the
shipped definition can be read in full before it is taken, next to the one in force. Wording-only
updates are counted separately so they can be taken together without weighing each one up.

Taking an update means deleting the override, at which point the entry follows Hephaestus again.
Keeping ours means re-stamping the override with the digest of the definition just declined, which
is what stops the same update being asked about twice — the next question comes with the next
genuine change. A replica of an older build never rolls the catalogue back, because it never wrote
anything down.

Retirement is instance policy: it stops an entry being offered to new workspaces and changes nothing
that already exists. Retiring an area also withholds the practices filed under it, since an area is
how those practices are presented; the confirmation says how many that is.

## What a workspace receives

A workspace is given the instance catalogue once, when it is created — every entry the instance
offers, whether Hephaestus shipped it or an administrator wrote it. From that moment the workspace's
practices are its own. Later catalogue changes never rewrite them.

That is deliberate, and it is visible rather than silent. Each workspace practice and area records
the slug it came from and the fingerprint it was copied at — two columns, no foreign key into the
catalogue, because a workspace's practice must survive its source being retired. Comparing three
fingerprints (what is running here, what it was copied from, what the instance offers now) gives the
workspace's practice administration what it shows: unchanged, changed here, or a newer catalogue
version available. Whether to take a newer version is the workspace's decision, and the mechanism
for taking it is separate work.

Equivalence is derived from the definition, not from the order edits happened in. A practice matches
its source while everything that reaches a detection run matches — criteria, precompute script,
slug, name, trigger events and area. Editing only what people read keeps the match; editing the
detection criteria drops it; editing them back restores it. For an area the comparison is how it
presents, deliberately excluding display order, because a workspace ordering its own areas is a
layout choice rather than an edit.

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
