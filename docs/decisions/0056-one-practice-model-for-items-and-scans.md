# ADR 0056: One practice model for items and scans

**Status:** Accepted
**Date:** 2026-10-14
**Authors:** Hephaestus maintainers

## Context

Practice reviews need one model across definitions, people, execution, results and feedback.
Scans also need durable selected-file membership after jobs expire.
Provider-specific records and duplicated definitions cannot express those facts safely.

## Decision drivers

- Keep every claim within captured and authorized evidence.
- Enforce workspace separation in SQL.
- Preserve product history independently of runtime cleanup.
- Use one vocabulary across code, interfaces and documentation.
- Give self-hosters a tested released-state migration.

## Considered options

1. Keep provider-specific verdict and execution records. This couples history to runtime and repeats provider meaning in core.
2. Add a parallel scan model. This duplicates definitions, outcomes and report logic.
3. Build a generic graph for future domains. Current review and delivery needs do not require it.
4. Use one item, review and result model with domain meaning at the boundary.

## Decision

Use option 4.
A practice version owns one immutable authored definition.
The database generates its name and review digest.
Operational feedback mode is separate from Reviewed by.

Core owns neutral contracts, people, tenancy, reviews, result admission and feedback invariants.
Domain modules own kinds, versions, role maps, file selection and evidence meaning.
Provider adapters own authentication, APIs, events, roster sync, capture, delivery and deep links.
Core does not import a provider or diff model.
Domain citation verifiers check side, version and claim basis after generic quotation checks.

Accounts identify people across provider identities and workspaces.
Roster sync can create unclaimed human accounts before sign-in.
An authenticated identity can claim that record or absorb only an unclaimed account.
Existing active accounts are never merged without that boundary.
Membership history remains after logical removal.
Account erasure explicitly removes affected proof and feedback before membership.

A review owns product history and many independent execution jobs.
Durable review membership records selected, judged, reused and withheld practice/item pairs.
A result has one evidence-bounded outcome and optional located problems.
The current claim stays occupied when an admin marks it incorrect or accepted.
Neither mark ends the result or revives older proof.
Accepted results leave open-problem counts, without changing outcomes or establishing repair.

Feedback retains exact result/problem support and separate pieces at each destination.
Preparation persists physical copies before provider writes.
Unknown visibility stays Unconfirmed and never permits an automatic repeat POST.
A confirmed sibling retains its appearance time.
Practice-page delivery uses the feedback record's first authenticated developer read.
No separate practice-card copy kind exists.

GitHub and GitLab scans pin monitored default-branch inventory.
Only one scan per workspace and one execution job per review can be active.
Reuse needs the same practice version, composed item version and complete frozen model method.

Incomplete or [suspect](../contributor/practice-review-pipeline.mdx#scans) inventory removes nothing.
File removal, marks and missed reads never prove repair or absence.
Slack and Outline support their own item evidence but have no scan driver or on-work feedback writer.

The [product vocabulary](../contributor/practice-feedback-language.md) owns product terms.
The [review glossary](../contributor/practice-review-glossary.mdx) owns operation terms and standard correspondence.
The [data model](../contributor/practice-feedback-schema.md) owns durable relationships.
The [pipeline](../contributor/practice-review-pipeline.mdx) owns stage boundaries.

## Consequences

All roles, runtime images and clients upgrade together.
One atomic migration transforms the released schema directly to this model.
It preserves retained identifiers, independent feedback pieces, unknown proof and provider write receipts.
Earlier queued runtime contracts are cancelled, not decoded by a compatibility path.
A digest does not reassess historical work.

The [migration procedure](../contributor/database-migration.mdx#prove-the-released-state-upgrade) owns upgrade verification.
Rollback restores a verified backup with the previous release.

This ADR supersedes model details in ADRs 0019, 0020, 0021, 0022, 0029 and 0050.
[ADR 0055](0055-practice-scripts-call-models-through-the-runner.md) owns the script contract.
Their surviving identity, evidence and runtime constraints remain in their owning records.

## Revisit trigger

Revisit when a shipped domain needs different person terms or citation coordinates.
Measured scan size can justify another storage design.
A new outcome needs a concrete evidence distinction that the current four cannot express.
