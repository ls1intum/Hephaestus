# Practice catalogue curation

The practice catalogue turns observable engineering habits into review criteria.

## Sources of truth

- Shipped catalogue:
  [`default-catalog.json`](https://github.com/ls1intum/Hephaestus/blob/main/server/src/main/resources/practices/default-catalog.json)
  and its referenced precompute scripts
- Effective instance catalogue: the database projection of the shipped catalogue plus explicit
  instance overrides, managed at `/admin/catalog` or through `/admin/practice-catalog`
- Workspace-specific catalogue: the workspace database, managed through the admin UI or REST API
- Workspace default seed: the effective available shipped practices, copied once when the workspace
  is initialized
- Persisted schema and initial bootstrap: Liquibase changelogs
- HTTP projections: generated OpenAPI
- Product vocabulary: [Practice feedback language](practice-feedback-language.md)

The Markdown references below support curation decisions. They are not persisted practice metadata
and are not machine-enforced.

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

Learner-facing fields should be plain and specific:

- **Name:** a short action or outcome.
- **Why it matters:** the failure the habit prevents and the practical benefit.
- **What good looks like:** one concrete, technology-neutral end state.
- **Criteria:** observable signals, exclusions, evidence requirements, and severity boundaries.

Avoid hype, grading language, and claims broader than the artifact being reviewed.

## Curation workflow

1. State the user problem and supported artifact.
2. Cite the evidence and classify the claim honestly.
3. Draft applicability, positive/negative signals, exclusions, and severity.
4. For a default that should reach every deployment, change the bundled catalogue and increment its
   `catalogRevision`. For an instance-only practice or variation, use `/admin/catalog`.
5. Add or update focused detection tests when the execution contract changes.
6. Review learner-facing copy in the admin UI and a representative delivered message.

On startup, a newer bundled revision is applied automatically to practices that still follow the
shipped definition. An instance edit creates a whole-practice override instead; later bundled
updates remain available without replacing that override. An administrator can return to the latest
bundled definition from the editor. A deployed catalogue revision is immutable: changing bundled
content without incrementing `catalogRevision` prevents startup, while an older application replica
cannot roll the catalogue back.

Every effective definition change appends an immutable revision. Retirement is an instance policy:
it hides a practice from the workspace-facing catalogue without changing its source relationship or
existing workspace copies. A practice removed from a newer bundle is retained in history and hidden
rather than deleted.

A workspace practice retains its curated source after local edits. Its current revision remains
equivalent only while its detector inputs match the curated revision; learner-facing edits preserve
that equivalence.

Workspace-specific practices should be created through the admin UI or API, not direct SQL, so
validation, ordering, revisions, and audit behavior remain intact.

Workspace defaults are snapshots, not subscriptions. A new workspace receives the current effective
bundled definitions with their provenance. Subsequent bundled or instance changes never rewrite
workspace copies; administrators decide whether to adopt a newer curated revision.

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
