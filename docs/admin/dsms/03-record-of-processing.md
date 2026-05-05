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

---

## Basics

### Responsible Department

Select: **TUM School of Computation, Information and Technology**

### Contact info of the person in charge

```text
Prof. Dr. Stephan Krusche, Head of AET
Research Group for Applied Education Technologies
TUM School of Computation, Information and Technology
Department of Computer Science
Boltzmannstraße 3, 85748 Garching b. München

Operational technical contact: ls1.admin@in.tum.de
```

### Responsible Person

Set to **Stephan Krusche (krusche@tum.de)** — head of the chair and Verantwortlicher for this PA. The field is auto-populated to the form-creator; reassign to Krusche.

### Additional responsible persons

**Felix Dietrich (felixtj.dietrich@tum.de)** — operational maintainer, retains edit access to the PA.

### Associated TUM Org identifier

**TODO before submitting** — look up the AET chair's TUM Org identifier in TUMonline (the field accepts a value like `TUVBDSB`). Not stored in the repo; ask the AET secretariat if unsure.

### Description and Purpose of Processing Activity

```text
Hephaestus is a self-hosted web platform operated by AET on TUM infrastructure at https://hephaestus.aet.cit.tum.de. Its purpose is to support project-based software-engineering teaching at TUM and the development work of AET research projects by giving each contributor feedback on their collaborative engineering work: for example, whether a pull request is small enough to review well, or whether a review reply addresses the question raised.

A workspace administrator (typically a TUM chair, lecturer, or research-group lead) connects one or more Git repositories from github.com or gitlab.lrz.de. Hephaestus then synchronises the pull/merge requests, issues, code reviews, review comments, and commit metadata authored in those repositories. The platform processes the activity of everyone who has authored there, whether or not they have signed in to Hephaestus.

The synchronised activity is analysed against a set of practices configured by the workspace administrator to produce findings about each contributor. Some of these judgements require reading and understanding natural-language text, such as the meaning of a code comment or the substance of a review reply. For those, the analysis uses an external LLM provider chosen by the administrator for the workspace. The automated practice review of a pull/merge request forwards the diff and surrounding discussion to the provider and posts the AI-generated finding as a comment on the pull/merge request. The conversational mentor lets contributors ask follow-up questions through an in-app chat, forwarding their messages and the relevant context to the same provider.

Contributors who sign in with their GitHub or LRZ-GitLab account get a personal dashboard summarising their findings and activity, access to the conversational mentor, and their account preferences. Sign-in adds the federated user identifier, username, display name, email, and avatar URL to what Hephaestus holds about that contributor. Workspace administrators can additionally enable a leaderboard, leagues, and achievements (all off by default) ranking contributors by their platform activity, and route notifications to Slack.

These workspace-level configuration choices (which repositories to connect, the practice catalog, the LLM provider, the leaderboard / leagues / achievements toggles, Slack notifications) are made by the workspace administrator and TUM/AET as joint controllers under Art. 26 GDPR. Hephaestus is built around the contributor's own development: findings serve the contributor and give the workspace administrator a way to deliver targeted feedback on practices during the project. The platform makes no automated decisions within the meaning of Art. 22 GDPR and feeds no grading, HR, or access-control pipeline. Contributors can disable AI-assisted feedback at any time through the in-app "AI review comments" toggle (Art. 21 GDPR).
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
Repository-activity artefacts authored by the contributor in the connected Git repositories (pull/merge requests, issues, code reviews, review comments, commit metadata) and AI guidance-assistant conversations.
```

### Recipient Categories

```text
External processors engaged by TUM/AET as controller. AVVs are in place at TUM/AET level for the AET-pool processors. Where a workspace administrator configures a different LLM endpoint (see below), the AVV is at that administrator's institution. Third-country transfers to U.S. recipients are covered by the EU-US Data Privacy Framework where the recipient is on the active DPF list, with SCCs Module 2 as fall-back.

- GitHub, Inc. / Microsoft Corporation (USA) — identity provider for GitHub sign-in and source-system API for connected GitHub repositories.

- An external LLM provider, chosen per workspace by the workspace administrator from any OpenAI-API-compatible HTTPS endpoint (configured by a baseURL, an API token, and a model name). The choice is a joint-controller decision under Art. 26 GDPR. The TUM-operated deployment uses Microsoft Azure OpenAI in an EU region under enterprise no-training terms by default. A workspace administrator may configure a different endpoint instead, such as OpenAI L.P. directly, an institution-level enterprise gateway, or a self-hosted model server.

- Salesforce, Inc. / Slack Technologies, LLC (USA) — workspace notifications when a workspace administrator has enabled Slack for that workspace.

Separate controller (not an Art. 28 processor):

- Leibniz-Rechenzentrum (LRZ) der BAdW — operator of gitlab.lrz.de. The platform receives the contributor's identity from gitlab.lrz.de OIDC and synchronises connected gitlab.lrz.de repositories. Inter-public-body transmission under Art. 5 Abs. 1 Nr. 1 BayDSG.
```

### URLs

```text
https://hephaestus.aet.cit.tum.de
```

---

## Legal Basis

### What is the legal basis of processing this activity? (Art. 6.1 GDPR)

Tick:

- [x] Art. 6.1a GDPR (consent) — for workspaces collecting explicit consent (e.g., the AET capstone course).
- [x] Art. 6.1b GDPR (contract / service request) — for voluntary sign-in by non-TUM contributors.
- [x] Art. 6.1e GDPR (public task) — for TUM/AET operation of the platform.

Do **not** tick 6.1f. Bavarian public bodies cannot rely on legitimate interest for tasks carried out in the performance of a statutory public duty (Art. 6(1) Unterabsatz 2 GDPR).

### Legitimate Interest Assessment

Field hidden unless 6.1f is ticked. Leave empty.

### Other legal basis (textarea)

```text
TUM/AET as platform operator: Art. 6(1)(e) GDPR i.V.m. Art. 2 BayHIG (Aufgaben der Hochschule: Forschung, Lehre, Wissens- und Technologietransfer) and Art. 4 Abs. 1 BayDSG.

Per-workspace lawful basis: anyone with a GitHub or LRZ-GitLab account can create a workspace on this platform. The workspace administrator and TUM/AET are joint controllers under Art. 26 GDPR for the workspace's processing; the workspace administrator invokes and is responsible for the lawful basis applicable to their workspace's contributors. Common bases:

- Art. 6(1)(a) GDPR — explicit consent collected by the administrator (e.g., the AET capstone course's application phase).
- Art. 6(1)(e) GDPR i.V.m. Art. 2 BayHIG — public-task activity by a TUM unit (e.g., regular courses; public open-source repositories such as ls1intum/Artemis).
- Workspace administrators outside TUM (e.g., external open-source maintainers, partner-institution researchers) cannot invoke Art. 6(1)(e) BayHIG; they invoke a basis available to them under Art. 6 GDPR — typically Art. 6(1)(a) consent or, where the administrator is a private body, Art. 6(1)(f) GDPR (legitimate interest, with their own LIA).

Voluntary sign-in by non-TUM contributors to use personal features: Art. 6(1)(b) GDPR.

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
- From GitHub and gitlab.lrz.de: identity at sign-in (GitHub OAuth, gitlab.lrz.de OIDC, brokered through Keycloak); repository activity via the GitHub App installation or workspace-configured access token; webhook events delivered to the platform's /webhooks endpoint.
- From the HTTP connection: IP address and request metadata captured in the application server's access log.
```

### Where is this data located and how is it stored?

```text
Self-hosted by AET at https://hephaestus.aet.cit.tum.de on AET-administered infrastructure at TUM. Application data in PostgreSQL; authentication state in a self-hosted Keycloak (separate PostgreSQL); webhook events and the practice-review job queue in NATS JetStream. Local working copies of monitored repositories may be stored on the host filesystem when practice-review code execution is enabled. Container stdout is rotated by the container runtime (Docker json-file driver, 50 MiB per file × 5 files retained per service). The application-server access log is retained for at most 14 days.

All primary data resides on TUM infrastructure within the EU. AI-assisted features additionally transmit the relevant code snippets and discussion to the workspace-configured LLM provider (default for the TUM-operated deployment: Microsoft Azure OpenAI in an EU region).
```

### What is your envisaged time for deletion / erasure of the data for this processing activity?

Leave the multi-select empty — no preset matches the per-category retention model. Use the Custom Erasure Time field below.

### Custom Erasure Time

```text
Mixed retention by category:

- Account-bound data (Keycloak account, federated identity links, analytics identity): removed on user-triggered account deletion via the in-app control.
- Contributor profile and activity data synchronised from GitHub / gitlab.lrz.de (login, name, email, avatar, authored issues / pull requests / comments / reviews, AI conversations, recognition signals, practice findings): retained while at least one workspace continues to track the contributor's repositories. Removed when the last workspace stops monitoring the source repository, or on operator-executed deletion against the production database on receipt of a verified erasure request.
- LLM-provider-side prompts: per the workspace's chosen provider's terms. For the TUM-operated default (Microsoft Azure OpenAI in an EU region), up to 30 days under the enterprise abuse-monitoring window.
- Application-server access log: at most 14 days; longer only for the duration of an active security incident, then deleted on closure.
- Container stdout: rotated by size by the container runtime (50 MiB × 5 files per service).
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
The in-app account-deletion control removes the user's Keycloak account, federated identity links, and analytics identity in PostHog. Erasure of the contributor profile row in the application database and its dependent records (workspace memberships, AI conversations, practice-finding feedback, recognition signals) is performed manually by AET operators against the production database. Source-side content on GitHub or gitlab.lrz.de is not modified by deletion in Hephaestus. Container stdout rotates automatically by size; the application-server access log is pruned by Tomcat's native 14-day retention. No long-term off-host backups are configured in the application repository at the time of submission; if the underlying VM is snapshotted by AET infrastructure operations, the operations team applies the standard snapshot retention.
```

### Specific Technical and Organisational Measures

```text
- Hosting: self-hosted by AET at https://hephaestus.aet.cit.tum.de on AET-administered infrastructure at TUM.
- Authentication and access control: federated identity via self-hosted Keycloak (GitHub OAuth, gitlab.lrz.de OIDC); workspace-scoped membership and role checks enforced at the application layer; least-privilege source-system access via per-workspace GitHub App installation or scoped access token.
- Encryption in transit: TLS-terminated at Traefik with Let's Encrypt; outbound calls to GitHub, gitlab.lrz.de, the LLM provider, and Slack are HTTPS-only.
- Logging and incident response: Tomcat access logs retained for at most 14 days with a minimised pattern; container stdout rotated by the Docker json-file driver (50 MiB × 5 per service); incidents affecting personal data reported to the TUM DPO within 72 h under Art. 33 / 34 GDPR.
- AI features: practice-review code execution runs inside per-job Docker sandboxes on internal-only Docker networks with no general egress; the only outbound path is a per-job, token-authenticated LLM proxy. Sandbox is off by default (`SANDBOX_ENABLED=false`) and is workspace opt-in. For OpenAI / Azure OpenAI providers, enterprise no-training terms apply at the AET tenancy level.
- Supply chain and source: source at github.com/ls1intum/Hephaestus (MIT). CI runs CodeQL (GitHub Default Setup), Trivy (filesystem and container image), TruffleHog secret detection, and Renovate dependency updates. Production images are cosign-signed and pulled from GHCR.
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

## Documents

The form's Documents section lists six categories of potentially-uploadable artefacts. Hephaestus uploads **none** — every relevant artefact either lives at a public URL the form already references, or pastes into a corresponding form field. Per-line stance:

| Form item | Stance |
|---|---|
| Joint Controller Agreement (Art. 26) | No upload. The Art. 26(2) Satz 2 publication of the essence is the privacy statement at §3.2 ([https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy)). The configuration acceptance flow when a workspace administrator first connects a repository is the operative agreement; there is no separate per-workspace signed JCA document to upload. |
| Data Processing Agreement (DPA) — LRZ | No upload (form explicitly says "this agreement exists centrally and does not need to be uploaded"). |
| Data Processing Agreement (DPA) — GitHub / Microsoft / OpenAI / Slack | No upload. These are AET-pool processors with AVVs at TUM/AET central level. The vendor-pool registration request is in *Other Remarks*. |
| Consent form | N/A. Processing is on Art. 6(1)(e) and 6(1)(b), not 6(1)(a). |
| Privacy information for data subjects | No upload. Public URL referenced in the form's *URLs* field: [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy). |
| Data flow diagram | No upload. The processing flow is described inline in *Description and Purpose* and *Recipient Categories*. The published privacy statement §3 elaborates further. |
| Ethics committee opinion | N/A. The TUM-operated deployment is an operational service, not a study; capstone-course data collection has separate ethics approval which is recorded against the *research project* PA, not this operational PA. |
| DPIA / risk assessment draft | No upload. DPIA-light posture documented in [`02-dpia-prescreen.md`](./02-dpia-prescreen.md); the §6 conclusion is referenced in this VVT and is available for the DSB to read in the repo. Upload only if the DSB explicitly requests it. |

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
