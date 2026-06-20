# Practice Catalogue — Curated, Grounded Reference

> Status: curated reference for the default practice catalogue shipped in
> `server/src/main/resources/practices/default-catalog.json` (**11 areas / 32 practices**). Companion
> to `practice-feedback-schema.md` (the entity reference) and ADR 0021. This document grounds each area
> in a body-of-knowledge **standards anchor** and assigns each practice a **credibility tier**, so the
> catalogue's claims can be defended source-by-source. Uses the canonical vocabulary throughout —
> *area*, never *goal*.

---

## 1. What this catalogue is — scope posture (threats to validity)

The instrument scope is deliberately narrow and stated up front, because the strongest objection to a
practice catalogue is over-reach:

> **The instrument scope is *observable individual acts on a single artifact mediated by an
> integration*** — a pull request, an issue, or a review thread, read through the integration that
> materialises it. Each practice judges a *workflow/craft habit* visible in that one artifact, never a
> system-level or correctness property.

Two consequences follow, and both are *named, deliberate omissions* rather than gaps:

1. **Architecture / system-level design is out of scope** (SWEBOK KA2 *Software Architecture*; the
   structural half of KA3 *Software Design*). Architecture is a system-level, cross-change property not
   attributable to one observable authoring act within one integration-mediated artifact. Adding an
   architecture area would force either low-precision findings ("this looks like a god class" — a
   correctness claim the detector is forbidden to make) or a near-always-`NOT_APPLICABLE` dead-weight
   area. The single observable *sliver* of design — capturing the **rationale** behind a significant
   decision — is covered by `records-significant-decisions-with-rationale`. This is the omission a
   committee is most likely to cite; it is answered here, not patched with a weak area.
2. **No practice asserts correctness.** Practices judge *habits* (did a test accompany the change? does
   the description state a why? is untrusted input neutralised before a sink?), never *outcomes* (does
   the code work? is the bug real?). The grading vocabulary stays out of learner-facing text; severity
   never exceeds `MAJOR` on a process habit alone.

**Credibility tiers.** Each practice carries one of three honesty labels so the catalogue does not
over-claim its evidence basis:

| Tier | Meaning |
| --- | --- |
| **peer-reviewed** | grounded in a peer-reviewed empirical study (controlled experiment or large-scale mining study) |
| **practitioner-canonical** | grounded in a canonical standard / practitioner source (SWEBOK, ISO/IEC 25010, OWASP, Google eng-practices, Conventional Commits) but **not** a controlled-outcome study |
| **practitioner-norm** | a community / gatekeeping convention with **no** controlled-outcome study |

**Two deferred slug renames.** Two practices carry a human `name`/`summary` that intentionally differs
from their slug, to remove a label↔source contradiction: the civility practice (slug reads *"ask rather
than demand"*) and the injection practice (slug reads *"validate and escape"*). Their **slugs are left
unchanged** — a slug is a finding fingerprint key (`FindingFingerprint.compute()` hashes `practiceSlug`),
so renaming it would orphan every prior finding, reaction, and feedback correlated through it. A slug
rename needs an explicit **fingerprint-remap migration** so prior findings re-correlate rather than
orphan; that remap is the gating step (the SCD-2 `practice_revision` history — see
`practice-feedback-schema.md` §3.8 — does not by itself remap fingerprints).

---

## 2. The 11 areas and their standards anchors

Each area carries a `standardsAnchor` — the body of knowledge that grounds the *grouping*. Anchors are
attached as metadata (citations), not as renames: the R9 doctrine is "keep the slug, add the citation".

### 2.1 `review-ready-work` — *Submitting review-ready work*
**standardsAnchor:** Google Engineering Practices, *The CL Author's Guide / Writing good CL
descriptions*; DORA *Working in small batches*. Grouping-level synthesis (no single canonical name owns
"make your change cheap to review").
- Google, *Code Review — The CL Author's Guide* — [https://google.github.io/eng-practices/review/developer/](https://google.github.io/eng-practices/review/developer/)
- Forsgren, Humble & Kim (2018), *Accelerate* / DORA — small-batch delivery — [https://dora.dev/capabilities/](https://dora.dev/capabilities/)

### 2.2 `acting-on-review-feedback` — *Acting on review feedback*
**standardsAnchor:** Google Engineering Practices, *Handling Reviewer Comments*; Hattie & Timperley
(2007), *The Power of Feedback* (feedback only changes the work when the recipient acts on the
feed-forward). The author-response loop is the most lightly studied area empirically — flagged.
- Google, *Handling Reviewer Comments* — [https://google.github.io/eng-practices/review/developer/handling-comments.html](https://google.github.io/eng-practices/review/developer/handling-comments.html)
- Hattie & Timperley (2007), Review of Educational Research 77(1):81–112 — [https://journals.sagepub.com/doi/10.3102/003465430298487](https://journals.sagepub.com/doi/10.3102/003465430298487)

### 2.3 `actionable-issue-authoring` — *Writing issues a maintainer can act on*
**standardsAnchor:** Bettenburg et al. (2008), *What Makes a Good Bug Report?* (FSE; 466 responses across
Apache/Eclipse/Mozilla — steps-to-reproduce, observed/expected most valued and hardest to supply);
SWEBOK KA *Problem Reporting* (configuration management / maintenance).
- Bettenburg, Just, Schröter, Weiss, Premraj & Zimmermann (2008), *What Makes a Good Bug Report?*, ACM SIGSOFT FSE — [https://thomas-zimmermann.com/publications/files/bettenburg-fse-2008.pdf](https://thomas-zimmermann.com/publications/files/bettenburg-fse-2008.pdf)

### 2.4 `constructive-code-review` — *Reviewing a teammate's work constructively*
**standardsAnchor:** CMMI v2.0 **Peer Reviews (PR)** — a standalone Practice Area in v2.0 (it lived
inside *Verification (VER)* in v1.3); Bacchelli & Bird (2013), *Expectations, Outcomes, and Challenges of
Modern Code Review* (understanding as the central act; knowledge transfer + team awareness as outcomes);
Sadowski et al. (2018), *Modern Code Review at Google* (five purposes: education, norms, gatekeeping,
accountability, history).
- CMMI v2.0 Peer Reviews (PR) — ISACA model; description: [https://spyro-soft.com/blog/process-areas-in-cmmi-2-0-model](https://spyro-soft.com/blog/process-areas-in-cmmi-2-0-model)
- Bacchelli & Bird (2013), ICSE — [https://sback.it/publications/icse2013.pdf](https://sback.it/publications/icse2013.pdf)
- Sadowski, Söderberg, Church, Sipko & Bacchelli (2018), ICSE-SEIP — [https://sback.it/publications/icse2018seip.pdf](https://sback.it/publications/icse2018seip.pdf)

### 2.5 `testing-discipline` — *Shipping tested changes*
**standardsAnchor:** SWEBOK v4.0 KA5 **Software Testing**; ISO/IEC 25010:2023 *Maintainability →
Testability* (one of the five named sub-characteristics); DORA *Test automation*.
- IEEE CS, *Guide to SWEBOK v4.0* (2024), KA5 Software Testing — [https://www.computer.org/education/bodies-of-knowledge/software-engineering](https://www.computer.org/education/bodies-of-knowledge/software-engineering)
- ISO/IEC 25010:2023 — [https://www.iso.org/standard/78176.html](https://www.iso.org/standard/78176.html)
- DORA — [https://dora.dev/capabilities/test-automation/](https://dora.dev/capabilities/test-automation/)

### 2.6 `code-craftsmanship` — *Writing maintainable code*
**standardsAnchor:** ISO/IEC 25010:2023 **Maintainability** `{Modularity, Modifiability, Analysability}`;
SWEBOK v4.0 KA4 **Software Construction**; DORA *Code maintainability* (org-level analogue). The
human-facing area name ("Writing maintainable code") *is* the ISO anchor; the folksy slug keeps the
unit of analysis honest — a per-change individual habit, not the standard's system/org-level quality
characteristic.
- ISO/IEC 25010:2023 Maintainability — [https://www.iso.org/standard/78176.html](https://www.iso.org/standard/78176.html)
- SWEBOK v4.0 KA4 Software Construction — [https://www.computer.org/education/bodies-of-knowledge/software-engineering](https://www.computer.org/education/bodies-of-knowledge/software-engineering)
- DORA, *Code maintainability* — [https://dora.dev/capabilities/code-maintainability/](https://dora.dev/capabilities/code-maintainability/)

### 2.7 `robust-error-handling` — *Handling failure robustly*
**standardsAnchor:** SWEBOK v4.0 KA4 *Software Construction* — the named sub-topics **"Error Handling,
Exception Handling, and Fault Tolerance"** and **"Assertions, Design by Contract, and Defensive
Programming"**; ISO/IEC 25010:2023 *Reliability* (fault tolerance, recoverability) + *Safety*.
- SWEBOK v4.0 KA4 — [https://www.computer.org/education/bodies-of-knowledge/software-engineering](https://www.computer.org/education/bodies-of-knowledge/software-engineering)
- ISO/IEC 25010:2023 — [https://www.iso.org/standard/78176.html](https://www.iso.org/standard/78176.html)

### 2.8 `secure-by-default-changes` — *Making secure-by-default changes*
**standardsAnchor:** OWASP ASVS v5.0.0 — **V1 Encoding and Sanitization** (output-side) and **V2
Validation and Business Logic** (input-side), split into two top-level chapters in v5.0; SWEBOK v4.0
**KA13 Software Security** (new in v4); OWASP Top 10 A03 Injection / A05 Misconfiguration / A06
Vulnerable Components.
- OWASP ASVS v5.0.0 V1 Encoding and Sanitization — [https://asvs.dev/v5.0.0/V1-Encoding-and-Sanitization/](https://asvs.dev/v5.0.0/V1-Encoding-and-Sanitization/)
- SWEBOK v4.0 KA13 Software Security — [https://www.computer.org/education/bodies-of-knowledge/software-engineering](https://www.computer.org/education/bodies-of-knowledge/software-engineering)
- OWASP Top 10:2021 — [https://owasp.org/Top10/2021/](https://owasp.org/Top10/2021/)

### 2.9 `decisions-and-documentation` — *Recording decisions and documentation*
**standardsAnchor:** Nygard (2011), *Documenting Architecture Decisions* (ADR — practitioner canon for
the decisions half); DORA *Documentation quality* (empirically tied to performance); SWEBOK *Software
Design / Construction* documentation. The decisions half is practitioner-canon, not peer-reviewed.
- Nygard (2011), *Documenting Architecture Decisions* — [https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
- DORA, *Documentation quality* — [https://dora.dev/capabilities/documentation-quality/](https://dora.dev/capabilities/documentation-quality/)

### 2.10 `delivery-and-version-control-discipline` — *Disciplined delivery and version control*
**standardsAnchor:** DORA *Version control* / *Trunk-based development* / *Working in small batches*
(empirically tied to delivery performance); SWEBOK v4.0 KA8 *Software Configuration Management*;
Conventional Commits v1.0.0 (a *convention*, not a standards body); OpenSSF Scorecard / SLSA for the
generated-artifact integrity hook.
- DORA capabilities — [https://dora.dev/capabilities/](https://dora.dev/capabilities/)
- Conventional Commits v1.0.0 — [https://www.conventionalcommits.org/en/v1.0.0/](https://www.conventionalcommits.org/en/v1.0.0/)
- OpenSSF Scorecard — [https://scorecard.dev/](https://scorecard.dev/) · SLSA v1.0 — [https://slsa.dev/spec/v1.0/levels](https://slsa.dev/spec/v1.0/levels)

### 2.11 `issue-traceability-and-lifecycle` — *Tracking and planning the work*
**standardsAnchor:** IEEE SEVOCAB *Requirements traceability* (follow a requirement's life forward and
backward); SWEBOK v4.0 KA8 *Configuration status accounting* + KA1 *Requirements*; CMMI *Configuration
Management* + *Requirements Development & Management*. The lifecycle half is lighter (practitioner +
one 2025 preprint).
- IEEE SEVOCAB / Requirements traceability — [https://pascal.computer.org/sev_display/index.action](https://pascal.computer.org/sev_display/index.action)
- SWEBOK v4.0 KA8/KA1 — [https://www.computer.org/education/bodies-of-knowledge/software-engineering](https://www.computer.org/education/bodies-of-knowledge/software-engineering)
- *Issue Tracking Ecosystems: Context and Best Practices* (2025) — [https://arxiv.org/pdf/2507.06704](https://arxiv.org/pdf/2507.06704)

---

## 3. The 32 practices — grounding + credibility tier

Each row: the practice's one-line grounding (study/standard + author/year) and its credibility tier.
"PR" = `PULL_REQUEST` artifact, "ISSUE" = `ISSUE` artifact.

### `review-ready-work`
| Practice (slug) | Artifact | One-line grounding | Tier |
| --- | --- | --- | --- |
| `scope-one-reviewable-change` | PR | Sadowski et al. 2018 (median ~24 LOC, 90% < 10 files) + di Biase et al. 2019 (controlled experiment: change decomposition reduces review false-positives) | **peer-reviewed** |
| `describe-what-and-why` | PR | Bacchelli & Bird 2013 (understanding is the central act of review → the description must enable it) + Google CL-description guidance | **peer-reviewed** |
| `ready-and-traceable-handoff` | PR | IEEE SEVOCAB requirements-traceability + Sadowski 2018 (history/accountability purpose) | **practitioner-canonical** |
| `commit-subjects-explain-each-change` | PR | Tian et al. 2022 (ICSE, *What Makes a Good Commit Message?*) + Conventional Commits (convention, not mandated) | **peer-reviewed** |
| `honours-linked-issue-acceptance-criteria` | PR | IEEE SEVOCAB requirements-traceability (forward/backward trace of a requirement to its delivery) + Sadowski 2018 history purpose | **practitioner-canonical** |

### `acting-on-review-feedback`
| Practice | Artifact | Grounding | Tier |
| --- | --- | --- | --- |
| `engaging-with-inline-review-comments` | PR | Hattie & Timperley 2007 (feedback changes the work only when acted on) + Bosu et al. 2015 (usefulness operationalised as an author code change) | **peer-reviewed** |
| `merged-past-unresolved-review-threads` | PR | Sadowski 2018 gatekeeping/accountability purposes + platform required-thread-resolution rules. **No controlled-outcome study** that merging past unresolved threads worsens outcomes — a norm, not an effect; the criteria are explicit it is *never* a claim the merged code is wrong | **practitioner-norm** |

### `actionable-issue-authoring`
| Practice | Artifact | Grounding | Tier |
| --- | --- | --- | --- |
| `issue-states-an-actionable-problem` | ISSUE | Bettenburg et al. 2008 (a good report states observed/expected + reproduction) | **peer-reviewed** |
| `issue-scoped-to-single-concern` | ISSUE | Bettenburg et al. 2008 (one concern per report — multi-bug reports are harder to act on) | **peer-reviewed** |
| `issue-has-checkable-outcome` | ISSUE | Bettenburg et al. 2008 (steps-to-reproduce / expected-vs-actual / a checkable done-state were the most-valued, hardest-to-supply elements) | **peer-reviewed** |

### `constructive-code-review`
| Practice | Artifact | Grounding | Tier |
| --- | --- | --- | --- |
| `leaves-useful-specific-review-comments` | PR | Bosu, Greiler & Bird 2015 (MSR; ~1.5M comments — useful ⇔ triggers a code change) | **peer-reviewed** |
| `reviews-respectfully-asks-rather-than-demands` *(named "Keep review comments civil and about the code, not the person")* | PR | Bosu et al. 2015 (politeness is an empirically load-bearing usefulness factor) + Google eng-practices ("comments about the code, never the developer"; direct guidance is endorsed — grounds civility, *not* interrogative mood) | **peer-reviewed** |
| `reviews-substantively-with-understanding` | PR | Bacchelli & Bird 2013 (understanding is the central, named act of review — anchors the slug) | **peer-reviewed** |

### `testing-discipline`
| Practice | Artifact | Grounding | Tier |
| --- | --- | --- | --- |
| `ships-tests-with-the-change` | PR | SWEBOK KA5 Software Testing + DORA test-automation + ISO 25010 Testability | **practitioner-canonical** |
| `keeps-the-test-suite-honest` | PR | Google testing practice (flaky/non-deterministic test management; test pyramid) + Fowler, *Eradicating Non-Determinism in Tests*. No single standards body owns it — a synthesis | **practitioner-norm** |

### `code-craftsmanship`
| Practice | Artifact | Grounding | Tier |
| --- | --- | --- | --- |
| `removes-duplication-instead-of-copy-pasting` | PR | SWEBOK KA4 Software Construction (complexity reduction / refactoring) + ISO 25010 Modularity/Reusability | **practitioner-canonical** |
| `keeps-functions-small-and-single-purpose` | PR | SWEBOK KA4 (construction quality, complexity reduction) + ISO 25010 Modularity/Analysability | **practitioner-canonical** |
| `leaves-the-code-clean-with-intent-revealing-comments` | PR | SWEBOK KA4 (coding standards; comments explaining intent) + ISO 25010 Analysability | **practitioner-canonical** |

### `robust-error-handling`
| Practice | Artifact | Grounding | Tier |
| --- | --- | --- | --- |
| `handles-errors-instead-of-swallowing-them` | PR | SWEBOK KA4 "Error Handling, Exception Handling, and Fault Tolerance" (named sub-topic) + ISO 25010 Reliability | **practitioner-canonical** |
| `validates-inputs-and-edge-cases-at-the-boundary` | PR | OWASP ASVS v5.0 **V2 Validation and Business Logic** (input-side) + Top 10 A03 / CWE-20 | **practitioner-canonical** |
| `avoids-unsafe-panics-and-chosen-crashes` | PR | SWEBOK KA4 "Assertions, Design by Contract, and Defensive Programming" + ISO 25010 Safety | **practitioner-canonical** |

### `secure-by-default-changes`
| Practice | Artifact | Grounding | Tier |
| --- | --- | --- | --- |
| `validates-and-escapes-untrusted-input` *(named "Neutralize untrusted input before it reaches a dangerous sink")* | PR | OWASP ASVS v5.0 **V1 Encoding and Sanitization** (output-side; taint→sink neutralisation) + Top 10 A03 Injection / CWE-79 / CWE-89. The sibling `validates-inputs-and-edge-cases-at-the-boundary` owns the V2 input-side — matching ASVS's own v5.0 chapter split | **practitioner-canonical** |
| `avoids-insecure-defaults-and-over-broad-permissions` | PR | OWASP Top 10 A05 Security Misconfiguration + Proactive Controls (secure-by-default configuration) | **practitioner-canonical** |
| `changes-dependencies-deliberately` | PR | OWASP Top 10 A06 Vulnerable & Outdated Components + OpenSSF Scorecard (Pinned-Dependencies) + SLSA (dependencies as build inputs) | **practitioner-canonical** |

### `decisions-and-documentation`
| Practice | Artifact | Grounding | Tier |
| --- | --- | --- | --- |
| `records-significant-decisions-with-rationale` | PR | Nygard 2011 ADR (record the *why* of a significant decision) — also the single observable design sliver carried in lieu of an architecture area | **practitioner-canonical** |
| `documents-public-api-and-behaviour-changes` | PR | DORA documentation-quality capability (empirically predicts performance) + SWEBOK KA4 documentation | **practitioner-canonical** |

### `delivery-and-version-control-discipline`
| Practice | Artifact | Grounding | Tier |
| --- | --- | --- | --- |
| `commits-are-atomic-and-cohesive` | PR | DORA small-batch capability + the practitioner-canonical "atomic commit" norm | **practitioner-canonical** |
| `excludes-generated-and-build-artifacts` | PR | OpenSSF Scorecard Binary-Artifacts (committed binaries are a supply-chain risk) + SLSA (outputs derive from versioned inputs). For committed generated *text* the honest grounding is review-noise / single-source-of-truth — scoped to *inadvertent / build-output* artifacts, with an *intended-version-controlled-generated-source* carve-out (an OpenAPI spec or router tree that moves in step with the change that regenerates it is intended movement) | **practitioner-canonical** |
| `branches-from-the-integration-branch` | PR | DORA trunk-based development + GitHub Flow / GitLab Flow | **practitioner-canonical** |

### `issue-traceability-and-lifecycle`
| Practice | Artifact | Grounding | Tier |
| --- | --- | --- | --- |
| `breaks-large-work-into-trackable-subtasks` | ISSUE | SWEBOK KA1 Requirements (decomposition) + CMMI Requirements Development & Management | **practitioner-canonical** |
| `triages-the-issue-with-labels-and-ownership` | ISSUE | Practitioner convention — no standards body owns an issue-triage taxonomy | **practitioner-norm** |
| `issue-closed-with-unmet-outcome` | ISSUE | *Issue Tracking Ecosystems* (2025, arXiv preprint) + practitioner convention (confirm the outcome before closing) | **peer-reviewed** *(preprint — weaker than a published study; flagged)* |

---

## 4. Curated names / criteria / metadata (no slug churn)

The following `default-catalog.json` entries touch only human-facing `name`/`summary`, `criteria` text,
or metadata — never a slug (identity key), a stored enum, or a DB CHECK.

1. **Civility practice** (`reviews-respectfully-asks-rather-than-demands`): name → *"Keep review comments
   civil and about the code, not the person"*. Drops "ask rather than demand" — Google eng-practices
   endorses direct guidance, so the grounded property is **civility + code-not-person**, not interrogative
   mood (Google; Bosu et al. 2015 politeness). **Slug deferred.**
2. **Injection practice** (`validates-and-escapes-untrusted-input`): name → *"Neutralize untrusted input
   before it reaches a dangerous sink"*. Drops "validate" — the practice now judges only taint→sink
   neutralisation (ASVS v5.0 V1); input validation is owned by its sibling (V2). **Slug deferred.**
3. **`excludes-generated-and-build-artifacts`**: added an *intended-version-controlled-generated-source*
   carve-out (mirroring the existing lockfile carve-out) so the practice no longer contradicts the repo's
   own commit-the-OpenAPI/client/ERD policy — it flags only inadvertent / build-output artifacts.
4. **`describe-what-and-why`**: learner-facing summary explains the *motivation* ("why this change, what
   problem it solves"); the reason-connective regex stays buried in the grader, not surfaced as the
   ostensive definition (progressive-disclosure fix).
5. **Metadata adds**: ASVS V1/V2 tags on the two injection-adjacent practices; ISO 25010 / SWEBOK KA4 /
   DORA tags on `code-craftsmanship`; `credibilityTier` labels on `merged-past-unresolved-review-threads`
   (practitioner-norm) and `excludes-generated-and-build-artifacts`.
6. **Developer-facing learner copy (all 32 practices)**: every practice now carries seeded `whyItMatters`
   (a learner-facing *explanation* — why the practice matters) and `whatGoodLooksLike` (a concrete
   **exemplar** — what good looks like). These feed the developer-facing layer served through
   `LearnerPracticeDTO` / `GET /practices/learner`; that projection is **criteria-free by construction**
   (it has no `criteria` field), and an authoring guard rejects detector verdict vocabulary
   (`OBSERVED`/`NOT_OBSERVED`/`NOT_APPLICABLE`) in `whatGoodLooksLike`, so the detection rubric never
   leaks into learner copy. See `practice-feedback-schema.md` §3.1 / §3a / §6.

**Rejected curation (named overreach):** an architecture/design area (§1.1 — out of scope by design); a
third injection practice (the input/output split already exists across two practices); a
`code-craftsmanship` → ISO `maintainable-code` slug rename (the human name already speaks ISO; the slug
rename is churn + a fingerprint break + a unit-of-analysis mismatch); rewording
`merged-past-unresolved-review-threads` (already the most carefully hedged practice — only a tier label
was added).

---

## 5. Source register

Peer-reviewed:
- Bacchelli & Bird (2013), *Expectations, Outcomes, and Challenges of Modern Code Review*, ICSE — [https://sback.it/publications/icse2013.pdf](https://sback.it/publications/icse2013.pdf)
- Sadowski, Söderberg, Church, Sipko & Bacchelli (2018), *Modern Code Review: A Case Study at Google*, ICSE-SEIP — [https://sback.it/publications/icse2018seip.pdf](https://sback.it/publications/icse2018seip.pdf)
- Bosu, Greiler & Bird (2015), *Characteristics of Useful Code Reviews*, MSR — [https://www.microsoft.com/en-us/research/publication/characteristics-of-useful-code-reviews-an-empirical-study-at-microsoft/](https://www.microsoft.com/en-us/research/publication/characteristics-of-useful-code-reviews-an-empirical-study-at-microsoft/)
- di Biase, Bruntink, van Deursen & Bacchelli (2019), *The effects of change decomposition on code review — a controlled experiment*, PeerJ CS — [https://pmc.ncbi.nlm.nih.gov/articles/PMC7924728/](https://pmc.ncbi.nlm.nih.gov/articles/PMC7924728/)
- Bettenburg, Just, Schröter, Weiss, Premraj & Zimmermann (2008), *What Makes a Good Bug Report?*, FSE — [https://thomas-zimmermann.com/publications/files/bettenburg-fse-2008.pdf](https://thomas-zimmermann.com/publications/files/bettenburg-fse-2008.pdf)
- Tian et al. (2022), *What Makes a Good Commit Message?*, ICSE
- Hattie & Timperley (2007), *The Power of Feedback*, Review of Educational Research 77(1):81–112 — [https://journals.sagepub.com/doi/10.3102/003465430298487](https://journals.sagepub.com/doi/10.3102/003465430298487)
- *Issue Tracking Ecosystems: Context and Best Practices* (2025, arXiv preprint) — [https://arxiv.org/pdf/2507.06704](https://arxiv.org/pdf/2507.06704)

Standards / canonical practitioner:
- IEEE CS, *Guide to SWEBOK v4.0* (2024) — [https://www.computer.org/education/bodies-of-knowledge/software-engineering](https://www.computer.org/education/bodies-of-knowledge/software-engineering)
- ISO/IEC 25010:2023 — [https://www.iso.org/standard/78176.html](https://www.iso.org/standard/78176.html)
- CMMI v2.0 (Peer Reviews PA) — [https://spyro-soft.com/blog/process-areas-in-cmmi-2-0-model](https://spyro-soft.com/blog/process-areas-in-cmmi-2-0-model)
- OWASP ASVS v5.0.0 V1/V2 — [https://asvs.dev/v5.0.0/V1-Encoding-and-Sanitization/](https://asvs.dev/v5.0.0/V1-Encoding-and-Sanitization/) · OWASP Top 10:2021 — [https://owasp.org/Top10/2021/](https://owasp.org/Top10/2021/)
- DORA capability catalog — [https://dora.dev/capabilities/](https://dora.dev/capabilities/)
- Google Engineering Practices — [https://google.github.io/eng-practices/review/](https://google.github.io/eng-practices/review/)
- Conventional Commits v1.0.0 — [https://www.conventionalcommits.org/en/v1.0.0/](https://www.conventionalcommits.org/en/v1.0.0/)
- Nygard (2011), *Documenting Architecture Decisions* — [https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
- OpenSSF Scorecard — [https://scorecard.dev/](https://scorecard.dev/) · SLSA v1.0 — [https://slsa.dev/spec/v1.0/levels](https://slsa.dev/spec/v1.0/levels)
- IEEE SEVOCAB / Requirements traceability — [https://pascal.computer.org/sev_display/index.action](https://pascal.computer.org/sev_display/index.action)
- Fowler, *The Practical Test Pyramid* — [https://martinfowler.com/articles/practical-test-pyramid.html](https://martinfowler.com/articles/practical-test-pyramid.html)
