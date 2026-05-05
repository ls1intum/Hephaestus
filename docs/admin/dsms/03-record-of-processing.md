---
id: record-of-processing
title: Record of Processing Activities (VVT)
description: Field-by-field paste-ready answers for the TUM DSMS form.
---

_Last updated: 2026-05-05._

Field-ordered to match the TUM DSMS "Create new PA" form at [https://dsms.datenschutz.tum.de/](https://dsms.datenschutz.tum.de/). Each heading below is the exact form-field label. Copy from the fenced block under it.

The audit context behind these answers (joint-controller analysis, processor list, DPIA pre-screen pointer, Personalrat posture) is at the end of the document, not in the form.

---

## Top of form

### Title

```text
Hephaestus – Practice-Aware Feedback for Software Projects
```

### Checkboxes

- [ ] Processing on Behalf — leave unticked. TUM/AET is the controller, not a processor.
- [x] Joint Controller — tick. Workspace administrators are joint controllers under Art. 26 GDPR (see audit context, §A).
- [x] Relevant for Subject Rights Request (SRR) — tick.

### Tags

Tick:

- `Webdienst`
- `Lehre`
- `Forschungsprojekt` (Hephaestus is also used in AET research projects)
- `pot. verallgemeinerbar`

---

## Basics

### Responsible Department

Select: **TUM School of Computation, Information and Technology**

### Contact info of the person in charge

```text
Felix Dietrich
AET — Research Group for Applied Education Technologies
TUM School of Computation, Information and Technology, Department of Computer Science
Boltzmannstraße 3, 85748 Garching bei München
felixtj.dietrich@tum.de
Operational contact: ls1.admin@in.tum.de
```

### Responsible Person

Pre-populated as the form-creator (Felix Dietrich, felixtj.dietrich@tum.de). Leave as-is.

### Additional responsible persons

Optional. Suggested: **Stephan Krusche (krusche@tum.de)** as group lead.

### Associated TUM Org identifier

**TODO before submitting** — look up the AET chair's TUM Org identifier in TUMonline (the field accepts a value like `TUVBDSB`). Not stored in the repo; ask the AET secretariat if unsure.

### Description and Purpose of Processing Activity

```text
Hephaestus gives early-stage software engineers in-process feedback on professional software-engineering practices — practices like reviewability, citing evidence, review dialogue, and follow-through on commitments. It is used by facilitators (course instructors, open-source maintainers, coaches) who want to support the contributors of a software project as they work, not after they finish.

A facilitator configures a workspace and connects one or more Git repositories on github.com or gitlab.lrz.de. Contributors sign in with their GitHub or LRZ-GitLab account, and Hephaestus reads what they have already published in the connected repositories — pull/merge requests, issues, code reviews, review comments, commit metadata, and the associated author profile fields — to produce practice findings backed by evidence and a recommended next action. Findings reach the contributor inside their own pull requests as the request moves toward merge, and on personal surfaces the contributor consults on their own initiative (a reflection dashboard and an in-app conversational mentor); a separate facilitator dashboard summarises the same findings for workspace instructors and coaches.

Per workspace, the facilitator can additionally enable leaderboards and gamification (visible to other contributors of the same workspace), Slack notifications, and AI-assisted feedback that sends the relevant code snippets and discussion to a workspace-configured LLM provider. Hephaestus is contributor-facing; it is not used as a personnel-monitoring or grading instrument, and the signals inform facilitator judgement, they do not replace it.
```

### Categories of data subjects of this processing

Tick all of:

- Students (TUM)
- Students (extern)
- Employees (TUM)
- Employees (extern)
- Other Website Visitors

### Other Data Subject Categories

Leave empty.

### Data Categories Description

Tick all of:

- Name(s)
- Contact details: email
- Image data
- Indicators of Behaviour
- IP address
- Social network data
- User IDs and Passwords

Do **not** tick `Examination and academic performance` — practice findings are advisory, not graded; ticking this field would misclassify Hephaestus as an examination system.

### Other data categories

```text
Repository-activity artefacts authored by the contributor in the connected Git repositories: pull/merge requests, issues, code reviews, review comments, commit metadata. AI guidance-assistant messages and the AI-generated responses. Practice-review findings (verdict, severity, evidence cues, reasoning, recommended next action). Recognition signals (leaderboard rank, league assignment, achievement progress). Account preferences, including the in-app "AI review comments" Art. 21 objection toggle.
```

### Recipient Categories

```text
External processors engaged by TUM/AET as controller:

- GitHub, Inc. / Microsoft Corporation (USA) — identity provider (OAuth) and source-system API for GitHub-side repositories. AVV at TUM/AET level. EU–U.S. Data Privacy Framework (active list); Standard Contractual Clauses Module 2 as fall-back.

- Microsoft Corporation, Azure OpenAI Service (USA / EU region) — default LLM provider for the TUM-operated deployment under enterprise no-training terms. EU-region tenancies process within the EU. AVV at TUM/AET level for the TUM-operated tenancy; AVV at the workspace administrator's institution where that institution supplies the API credentials. DPF-active; SCCs Module 2 as fall-back.

- OpenAI, L.P. (USA) — alternative workspace-configurable LLM provider. AVV as above. DPF-active; SCCs Module 2 as fall-back.

- Salesforce, Inc. / Slack Technologies, LLC (USA) — workspace notifications and engagement digests when the workspace administrator has enabled Slack. AVV at TUM/AET level. DPF-active; SCCs Module 2 as fall-back.

Separate controller (not Art. 28 processor):

- Leibniz-Rechenzentrum (LRZ) der Bayerischen Akademie der Wissenschaften — operator of gitlab.lrz.de, the source system for GitLab-side repositories. Inter-public-body transmission under Art. 5 Abs. 1 Nr. 1 BayDSG; LRZ operates under the BAdW-Satzung. LRZ and TUM/AET are separate controllers under Art. 4(7) GDPR for the data each body processes on its own infrastructure.

Internal recipients: AET administrators and developers (operation, maintenance, support); workspace members (workspace-level findings and dashboards as described in §6 of the privacy statement at https://hephaestus.aet.cit.tum.de/privacy).

Anonymous open web: the upstream open-source project's GitHub contributor list at /contributors, and any workspace whose administrator has explicitly marked it publicly viewable.

No sale, no advertising recipients, no brokers.
```

### URLs

```text
https://hephaestus.aet.cit.tum.de
```

---

## Legal Basis

### What is the legal basis of processing this activity? (Art. 6.1 GDPR)

Tick:

- [x] Art. 6.1b GDPR (request from / contract with data subject) — for non-TUM contributors signing in
- [x] Art. 6.1e GDPR (performance of task carried out in public interest) — for the TUM teaching task

Do **not** tick 6.1f. Bavarian public bodies cannot rely on legitimate interest for tasks carried out in the performance of a statutory public duty (Art. 6(1) Unterabsatz 2 GDPR).

### Legitimate Interest Assessment

Field hidden unless 6.1f is ticked. Leave empty.

### Other legal basis (textarea)

```text
Art. 6(1)(e) GDPR i.V.m. Art. 2 BayHIG (Aufgaben der Hochschule: Forschung, Lehre, Wissens- und Technologietransfer) and Art. 4 Abs. 1 BayDSG (Rechtmäßigkeit der Verarbeitung im Bereich der bayerischen öffentlichen Stellen). The same basis covers application-server security logs (operation and security of a university IT service).

Art. 6(1)(b) GDPR for non-TUM contributors (performance of the service requested by signing in).

Contributors may object under Art. 21 GDPR to the AI-assisted feedback channel via the in-app "AI review comments" toggle; objection stops future transmissions but does not by itself delete previously generated findings.

Keycloak session cookies and theme-preference localStorage: § 25 Abs. 2 Nr. 2 TDDDG (technisch unbedingt erforderlich) i.V.m. Art. 6(1)(e) GDPR.
```

### What is the legal basis for processing special data categories of personal data? (Art. 9.2 GDPR)

Leave empty. Hephaestus processes no Art. 9 special-category data.

### Other legal basis (multi-select)

Tick:

- [x] Art. 4.1 BayDSG (Bavarian data protection act)

---

## Data and Deletion

### How was this data collected? What was the data source?

Tick:

- [x] Data received from third parties
- [x] Directly from the data subject

### Other data sources

```text
Data received from third parties means: from federated identity providers (GitHub, gitlab.lrz.de) via OAuth / OIDC during sign-in, and from the source-system APIs (GitHub, gitlab.lrz.de) using the workspace-configured installation or access token (repository events and artefacts authored by the contributor).

Directly from the data subject means: profile settings, notification preferences, AI-assistant messages, the Art. 21 objection switch.

Plus from the underlying HTTP connection: IP address and request metadata captured in the application server's native access log.
```

### Where is this data located and how is it stored?

```text
Self-hosted by AET on TUM infrastructure (VM provided by the TUM ITO) at https://hephaestus.aet.cit.tum.de. Application data resides in PostgreSQL on the same host. Authentication state in self-hosted Keycloak federating GitHub OAuth and gitlab.lrz.de OIDC. Application-server access logs on the host with 14-day retention. Container stdout rotated by the host's container runtime (50 MiB per file × 5 files retained per service).

All primary data resides on TUM infrastructure within the EU. AI-assisted features additionally transmit relevant code snippets and discussion to the workspace-configured LLM provider (default for the TUM-operated deployment: Microsoft Azure OpenAI in an EU region under enterprise no-training terms).

No scheduled off-host backups are in place at the time of submission. Establishing a scheduled backup regime with a documented restore drill is an open AET-operations item under Art. 32(1)(c) GDPR resilience.
```

### What is your envisaged time for deletion / erasure of the data for this processing activity?

Leave the multi-select empty — no preset matches the per-category retention model. Use the Custom Erasure Time field below.

### Custom Erasure Time

```text
Mixed retention by category:

- Account-bound data (identity, profile, settings, recognition signals, AI conversations, practice findings): retained for the lifetime of the account; removed on user-triggered account deletion.
- Repository activity synchronised from GitHub / gitlab.lrz.de: retained while the repository remains configured for the workspace; deletion in Hephaestus does not affect the source-side content on GitHub or gitlab.lrz.de.
- LLM-provider-side prompts: up to 30 days under the provider's enterprise abuse-monitoring window; shorter where Zero Data Retention is contracted for the workspace.
- Application-server access logs: at most 14 days; longer only for the duration of an ongoing security incident, then deleted at closure.
- Container stdout: rotated by size by the host's container runtime (50 MiB × 5 files per service).
```

### What is your reasoning for the erasure time above?

```text
Hephaestus is contributor-facing. Account-bound data exists to give the data subject continuity of feedback over the lifetime of their participation, and is removed the moment they decide to leave. Server-side logs and container stdout are bounded to the shortest period that still allows incident investigation under Art. 32(1) GDPR.
```

### Who is responsible for the deletion?

```text
AET operations team, ls1.admin@in.tum.de. User-initiated account deletion is processed via the in-app account-deletion control. Admin-initiated deletion on behalf of a data subject is processed by AET operators on receipt of a verified request through the TUM DPO (beauftragter@datenschutz.tum.de).
```

### How is deletion guaranteed?

```text
Account deletion removes the user record from the application database and from Keycloak; cascaded relationships remove dependent records (workspace memberships, AI conversations, recognition signals). Repository-activity records authored by the deleted user are anonymised in place (author reference removed) since they remain part of the historical workspace artefact set. Container stdout rotates automatically by size; access logs are pruned by the application server's native retention policy. No long-term off-host backups are in place at the time of submission, so deletion does not need to propagate to backup media.
```

### Specific Technical and Organisational Measures

```text
- Hosting and isolation: self-hosted by AET on TUM infrastructure inside the AET tenancy of the TUM ITO; outbound traffic restricted to documented endpoints (federated IdPs, source-system APIs, the workspace-configured LLM provider, Slack when enabled).
- Authentication and access control: federated identity via Keycloak (GitHub OAuth, gitlab.lrz.de OIDC); workspace-scoped membership and role checks; least-privilege source-system access via per-workspace GitHub App installation or scoped access token.
- Encryption in transit: TLS 1.2+ on all external endpoints; outbound calls to GitHub, gitlab.lrz.de, the LLM provider, and Slack over HTTPS.
- Logging and incident response: 14-day application-server access-log retention; container stdout rotated by host runtime (50 MiB × 5 per service); incidents reported to the TUM DPO under Art. 33 / 34 GDPR; documented breach-notification path.
- Sandboxing of code-executing AI features: practice-review code execution runs inside per-job Docker sandboxes behind a per-job LLM proxy; off by default (workspace opt-in).
- Data minimisation in AI calls: only the relevant code snippets and discussion are forwarded to the LLM provider; enterprise no-training terms in place; Zero Data Retention contractable per workspace.
- Source: github.com/ls1intum/Hephaestus (MIT). Supply-chain hygiene via Dependabot and CodeQL on the upstream repository.

Full per-category detail in 04-toms.md.
```

---

## Vendors / Applications

The form will say "no external processors" because GitHub / Microsoft / OpenAI / Slack aren't yet registered to this PA in the AET vendor pool. **Don't fix this in the Vendors section** — use the Other Remarks field below to ask the DPO to add them. The full list with AVV status is in the *Recipient Categories* paste above and in `05-processor-checklist.md`.

---

## Others

### Other Remarks

```text
Bitte folgende Auftragsverarbeiter zum AET-Pool hinzufügen, soweit noch nicht vorhanden: GitHub Inc. / Microsoft Corp. (USA), Microsoft Corp. (Azure OpenAI Service, USA/EU), OpenAI, L.P. (USA), Salesforce / Slack Technologies, LLC (USA). Beschreibungen unter "Recipient Categories"; Drittlandtransfers durch das EU–US Data Privacy Framework und Standardvertragsklauseln Modul 2 (jeweils im Rahmen des einschlägigen Enterprise-AVV) abgedeckt.
```

### Comment from the official data protection officer

Read-only. Filled by the DSB after submission.

---

## Submit

- **New State** dropdown → **Submitted**.
- Click **Save**.

Save before navigating between sections — the form does not auto-save.

---

## Audit context (not pasted into the form)

### A. Joint controllers (Art. 26 GDPR)

**Workspace administrators** (typically TUM chairs, lecturers, research-group leads, or — for AET research-project workspaces — facilitators of those projects) are joint controllers with TUM/AET for six workspace-configurable decisions documented in §3.2 of the privacy statement: which Git repositories are synchronised; which LLM provider and credentials the workspace uses; whether Slack notifications are routed; whether leaderboards / leagues / achievements are enabled; the workspace's Practice catalog; and whether practice reviews are auto-triggered on new pull/merge requests.

**Allocation of duties (Art. 26(2) Satz 1 GDPR):** TUM/AET is responsible for Art. 13/14 information duties (via the privacy statement), platform-level TOMs (Art. 32), breach notification (Art. 33/34), the DPIA posture (Art. 35), and this VVT. The workspace administrator is responsible for the lawfulness of the source-system authorisation, for informing the workspace's contributors that their repository artefacts are ingested, and for any LLM-provider credentials supplied by their own institution being backed by a processing agreement at that institution's level. The essence of the arrangement is published at §3.2 of the privacy statement (Art. 26(2) Satz 2). TUM/AET is the single point of contact for data-subject rights.

### B. Processors and recipients

Full per-processor AVV status, sub-processor exposure, and EDPB 07/2020 analysis for LRZ in [`05-processor-checklist.md`](./05-processor-checklist.md).

### C. DPIA posture

DPIA-light. Trigger conditions for opening a full DPIA are listed in [`02-dpia-prescreen.md`](./02-dpia-prescreen.md) §6.

### D. Personalrat (Art. 75a BayPVG)

Not triggered at the platform level. Hephaestus is suitable for displaying contributor activity but is not deployed by AET as a personnel-evaluation, performance-management, or behaviour-monitoring instrument. The platform is contributor-facing: no Dienststelle-segmented dashboard, no HR export, no manager-facing roll-up.

### E. Special categories (Art. 9 / Art. 10) and Art. 22

None. No special-category data is processed. No Art. 22 automated decisions: AI-assisted features produce advisory findings only and feed no automated grading, assessment, HR, or access-control pipeline.

### F. Information duty (Art. 13 / 14)

Privacy statement: [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy). Imprint: [https://hephaestus.aet.cit.tum.de/imprint](https://hephaestus.aet.cit.tum.de/imprint). Markdown source under [`webapp/public/legal/profiles/tumaet/`](https://github.com/ls1intum/Hephaestus/tree/main/webapp/public/legal/profiles/tumaet).

### G. Data-subject rights

Primary contact: [beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de) (TUM DPO). Operational queries: [ls1.admin@in.tum.de](mailto:ls1.admin@in.tum.de). Workspace-specific questions can additionally be addressed to the workspace administrator (privacy statement §3.2).
