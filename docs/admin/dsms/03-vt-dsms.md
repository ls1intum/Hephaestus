---
id: vt-dsms
title: DSMS Verarbeitungstätigkeit
description: Copy-paste answers for the DSMS follow-up questionnaire.
---

# Hephaestus — DSMS Verarbeitungstätigkeit (VT)

Copy-paste ready. Ordered to match the DSMS "Create new PA" form and follow-up questionnaire. Submit at: [https://dsms.datenschutz.tum.de/](https://dsms.datenschutz.tum.de/).

---

## Step 1 — "Create a new processing activity"

### Title

```
Hephaestus – Practice-Aware Guidance Platform for Software Projects
```

### Description and Purpose (min. 200 characters)

```
Hephaestus is a practice-aware guidance platform for software projects, operated by the Research Group for Applied Education Technologies (AET, Prof. Krusche) at the Technical University of Munich. It supports project-based software-engineering teaching at TUM and the software-development work of AET research projects. For each Project (a workspace and its configured Git repositories), the platform observes repository Events, detects workspace-defined Practices in the Artifacts produced by Contributors (pull/merge requests, issues, reviews, comments), records immutable advisory Findings, and delivers adaptive Guidance back to the Contributor. Optional features include dashboards, workspace engagement and recognition signals (leaderboards, leagues, achievements), a conversational guidance assistant, and automated practice reviews that call an external LLM provider configured per workspace. Authentication is federated through Keycloak (GitHub OAuth + gitlab.lrz.de OpenID Connect). Personal data is processed only to provide the service, ensure reliable and secure operation, and troubleshoot incidents.
```

(length check: ≥ 200 chars ✓)

### Category

Select: **Administration / Teaching / Other** (`other`).

### Tags

- `Webdienst`
- `Lehre`
- `KI-gestützt`
- `pot. verallgemeinerbar`

---

## Step 2 — Follow-up questions

### 1. Name & contact of the responsible unit (Art. 30(1)(a))

```
Responsible unit:   Research Group for Applied Education Technologies (AET)
                    TUM School of Computation, Information and Technology
                    Department of Computer Science
                    Boltzmannstraße 3, 85748 Garching bei München

Head of unit:       Prof. Dr. Stephan Krusche (per TUM website)
Hephaestus support: Via GitHub Issues at https://github.com/ls1intum/Hephaestus/issues
                    Operational contact: ls1.admin@in.tum.de
                    Data-protection requests: beauftragter@datenschutz.tum.de (TUM DPO)
```

### 2. Joint controllers (Art. 26 GDPR)

**Workspace administrators** (typically TUM chairs, lecturers, or research-group leads) are joint controllers with TUM/AET for the six workspace-configurable decisions documented in §3.2 of the privacy statement:

1. which Git repositories are synchronised into the workspace;
2. which LLM provider and which credentials are used by the workspace's AI-assisted features;
3. whether workspace notifications are routed to Slack;
4. whether leaderboards, leagues, and achievements are enabled for peer visibility;
5. the workspace's Practice catalog;
6. whether practice reviews are auto-triggered on new pull/merge requests.

TUM/AET is the single point of contact for data-subject rights. The essence of the Art. 26(2) Satz 2 arrangement is made available to data subjects via §3.2 of the privacy statement.

### 3. Processors (Art. 28 GDPR) — each with AVV status

See `05-avv-checklist.md` for the full table. In summary:

- **GitHub, Inc. / Microsoft Corporation** — identity provider (OAuth) and source-system API; Art. 28 processor for the data Hephaestus pulls via the GitHub App installation on behalf of the controller. AVV in place at the TUM/AET level.
- **OpenAI, L.P.** — LLM provider for workspaces configured to use it. AVV at TUM/AET level for the TUM-operated tenancy; AVV at the workspace administrator's institution level when that institution supplies the API credentials (shared-responsibility model).
- **Microsoft Corporation (Azure OpenAI Service)** — LLM provider for workspaces configured to use it. Region-configurable; EU-region deployments process within the EU. AVV as above.
- **Anthropic, PBC** — LLM provider for workspaces configured to use it. AVV as above.
- **Salesforce, Inc. / Slack Technologies, LLC** — workspace notifications when the workspace administrator has enabled Slack for the workspace. AVV in place at TUM/AET level.
- **TUM SMTP relay** operated internally by TUM Informatik — in-house; covered by TUM internal framework, not an external AVV.

The **Leibniz-Rechenzentrum (LRZ) der BAdW** is **not** an Art. 28 processor; gitlab.lrz.de runs on LRZ infrastructure under a public-body cooperation framework (§ 16 Abs. 1 Satz 2 BayHIG + BAdW-Satzung). LRZ and TUM each act as separate controllers (Art. 4(7) GDPR) for the data each body processes on its own infrastructure.

### 4. Data Protection Officer

Pre-populated by DSMS. Verify:

- TUM DPO office: **[beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de)**.

### 5. Purposes of processing (Art. 30(1)(b))

1. Authenticate Contributors and establish an authenticated session (Keycloak + federated IdPs).
2. Synchronise repository Events and Artifacts from configured source systems (GitHub, gitlab.lrz.de) to populate the workspace.
3. Detect workspace-defined Practices in synchronised Artifacts and produce advisory Findings.
4. Deliver adaptive Guidance back to Contributors via dashboards, the conversational guidance assistant, and automated practice-review comments on pull/merge requests.
5. Surface workspace engagement and recognition features (leaderboards, leagues, achievements) where the workspace administrator has enabled them.
6. Deliver workspace notifications over email (TUM SMTP) and — where the workspace administrator has enabled it — Slack.
7. Operate the service reliably and securely (server logs, backups) and troubleshoot incidents.

### 6. Name of IT system / procedure

```
Hephaestus (https://hephaestus.aet.cit.tum.de)
Stack:
  - Browser SPA:        React 19 / Vite / TanStack Router (TypeScript)
  - Application server: Spring Boot 3.5 / Java 21 / PostgreSQL
  - Intelligence:       Python FastAPI for AI-assisted features
  - Identity:           Self-hosted Keycloak (GitHub OAuth + gitlab.lrz.de OIDC)
  - Review sandbox:     Isolated Docker containers with per-job LLM proxy
  - Reverse proxy:      Traefik v3 / Let's Encrypt
  - Deployment:         Docker Compose on AET servers at TUM
Source (MIT): github.com/ls1intum/Hephaestus
```

### 7. Legal basis (cite GDPR article + national norm)

| Processing | Legal basis |
|---|---|
| Core service (authentication, repository sync, practice detection, guidance) for TUM Contributors | **Art. 6(1)(e) GDPR** i.V.m. **Art. 4 Satz 1 BayHIG** and **Art. 25 Abs. 1 BayDSG** (public-interest task: teaching and operation of university IT services) |
| Core service for non-TUM Contributors (external open-source contributors, partner-university members) | **Art. 6(1)(b) GDPR** (performance of the service the user requested by signing in) |
| AI-assisted features (guidance assistant, practice review) | Same as the row above; Contributors may object under **Art. 21 GDPR** via the "AI review comments" profile toggle |
| Optional individual notification email address | **Art. 6(1)(a) GDPR** (consent; revocable at any time in profile settings) |
| Server logs & reverse-proxy logs | **Art. 6(1)(e) GDPR** i.V.m. **Art. 4 Satz 1 BayHIG**, **Art. 25 Abs. 1 BayDSG**, **§ 8 BayDiG** (operation and security of a university IT service) |
| Keycloak session cookies and theme-preference localStorage | **§ 25 Abs. 2 Nr. 2 TDDDG** (strictly necessary for a service explicitly requested by the user) + Art. 6(1)(e) GDPR |

### 8. Categories of data subjects (Art. 30(1)(c))

Tick in DSMS:

- Students (TUM)
- Employees (TUM) — lecturers, tutors, AET staff, workspace administrators
- Students (extern) — from partner universities contributing to shared projects
- Other Website Visitors — unauthenticated visitors of public pages (imprint, privacy)

### 9. Categories of personal data

- **Identity & authentication (Keycloak):** external user ID at the identity provider (GitHub user ID or `sub` claim from gitlab.lrz.de), username / login, email, full name, avatar URL, profile URL.
- **Development activity (synchronised from source systems):** pull/merge requests, issues, code reviews, review comments, commit metadata, repository collaborator and team-membership metadata, and profile information of authors of these artifacts.
- **Account settings & recognition:** notification preferences, UI display options, optional notification email address (opt-in), workspace memberships and roles, leaderboard rank, league assignment, achievement progress.
- **AI-assisted features:** guidance-assistant messages + AI-generated responses, conversation threads, feedback (helpful / not helpful), practice-review findings (verdict, severity, evidence, reasoning), guidance text delivered back to the Contributor.
- **Server logs (14-day cap):** IP address, timestamp, HTTP method, URL, status code, bytes transferred, user-agent, referrer.
- **Browser-side storage:** Keycloak session cookies; theme preference in localStorage. No identifying data in localStorage.

### 10. Special categories (Art. 9 / Art. 10)

**None.** Hephaestus does not process health, biometric, genetic, racial, religious, political, trade-union, sex-life, sexual-orientation, or criminal-conviction data. Contributors are warned in the privacy statement not to enter third-party personal data into commits, reviews, or practice-review diffs.

### 11. Categories of recipients (Art. 30(1)(d))

- **Internal:** AET administrators / developers (operation, maintenance, support); workspace members (see workspace-level Findings and dashboards as described in §6 of the privacy statement).
- **External (Art. 28 processors), as configured per workspace:** GitHub / Microsoft; the LLM provider configured for the workspace (OpenAI / Azure OpenAI / Anthropic); Slack (when enabled); the TUM SMTP relay.
- **External (separate controllers, not Art. 28):** Leibniz-Rechenzentrum der BAdW for gitlab.lrz.de integration.
- **No sale, no advertising recipients, no brokers.**

### 12. Third-country transfers (Art. 30(1)(e))

All U.S.-based recipients are certified under the EU–U.S. Data Privacy Framework; the primary safeguard is the Commission adequacy decision under Art. 45(3) GDPR in relation to the DPF. Standard Contractual Clauses pursuant to Art. 46(2)(c) GDPR are contracted as a fall-back for any processing not covered by the recipient's DPF certification.

- **GitHub, Inc. / Microsoft Corporation** — DPF-certified; SCCs as fall-back. Azure OpenAI in a European region processes data within the EU.
- **OpenAI, L.P.** — DPF-certified; SCCs as fall-back.
- **Anthropic, PBC** — DPF-certified; SCCs as fall-back.
- **Salesforce, Inc. (Slack)** — DPF-certified; SCCs as fall-back.

### 13. Retention periods per data category (Art. 30(1)(f))

| Category | Retention |
|---|---|
| Identity data in Keycloak + Hephaestus DB | Account lifetime; removed on user-triggered account deletion |
| Development activity synchronised from GitHub / gitlab.lrz.de | For as long as the repository and workspace are configured; source-side content on GitHub / gitlab.lrz.de is not affected by deletions inside Hephaestus |
| Account settings, notification prefs, recognition signals | Account lifetime |
| Guidance-assistant conversations | Account lifetime; deletable on request |
| Practice-review Findings | Workspace lifetime; deletable on request |
| LLM-provider-side prompts | Up to 30 days (enterprise default abuse-monitoring); shorter where Zero Data Retention has been negotiated per workspace |
| Server access logs (app server + reverse proxy) | **Hard 14-day maximum** under normal operations; longer only where strictly necessary for an ongoing security incident, then deleted at closure |
| Backups (PostgreSQL, Keycloak realm) | Per AET operational baseline — verify against the live backup-job configuration before submission |

### 14. Technical and Organizational Measures (Art. 30(1)(g) + Art. 32)

See `04-toms.md`. Paste into the DSMS TOMs field or upload as an attachment.

### 15. Information-duty fulfilled (Art. 13/14)

Privacy statement at: [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy)
Imprint at: [https://hephaestus.aet.cit.tum.de/imprint](https://hephaestus.aet.cit.tum.de/imprint)

Both are versioned in the repository under `webapp/public/legal/profiles/tumaet/`.

### 16. Automated decision-making / profiling (Art. 22)

**No Art. 22 decisions.** AI-assisted features produce advisory Findings and Guidance only. They are not consumed by any automated grading, assessment, HR, or access-control pipeline operated by Hephaestus. A human instructor acting on information from Hephaestus dashboards remains accountable for that decision under their own process, outside Hephaestus.

### 17. DPIA pre-check (Art. 35 GDPR)

See `02-dsfa-prescreen.md`. Conclusion: **DPIA-light (may upgrade).** The AI-assisted feature surface is on the "elevated risk" side of the BayLfD innovative-technology criterion; mitigations in §5 of the pre-screen (no-training enterprise API terms, per-job LLM proxy, Art. 21 objection switch, shared-responsibility disclosure, 14-day log cap) replace a full DPIA at the current scope.

### 18. Personalrat involvement (Art. 75 BayPVG)

**Not triggered.** Hephaestus does not monitor TUM staff performance or behaviour. Staff who use the platform as mentors, lecturers, or administrators do so on the same footing as any other Contributor. Leaderboards and league signals are student-facing engagement features, opt-in per workspace, and not consumed by any HR process.

### 19. IT-Sicherheitsformular (TUM wiki)

Not applicable as a separate upload; Hephaestus is self-hosted by AET on TUM infrastructure under the AET operational baseline. See `04-toms.md`.

### 20. Source of data

- Directly from the data subject: profile settings, notification preferences, optional notification email, guidance-assistant messages, feedback.
- From federated identity providers (GitHub, gitlab.lrz.de) via OAuth / OIDC during sign-in.
- From source-system APIs (GitHub, gitlab.lrz.de) via the workspace-configured installation / access token: repository Events and Artifacts authored by the Contributor.
- From the underlying HTTP connection: IP address, user-agent (standard web-server logging, 14-day cap).

### 21. Data-subject rights contact

Primary: [beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de) (TUM DPO). Operational queries: [ls1.admin@in.tum.de](mailto:ls1.admin@in.tum.de) (AET admins). Data subjects may additionally address the workspace administrator directly for questions about the workspace-specific configuration (§3.2 of the privacy statement).

### 22. Attachments to upload in DSMS

- Privacy statement snapshot (export of [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy), PDF).
- `02-dsfa-prescreen.md` (DPIA pre-check / DPIA-light documentation).
- `04-toms.md` (TOMs).
- `05-avv-checklist.md` (Art. 28 checklist; lists every external processor and the status of its DPA).

### 23. Status

Set to **Submitted** after all fields are filled and attachments uploaded.

---

## Open items that require confirmation before submission

- Confirm the 14-day server-log retention cap matches the deployed log-rotation config on `hephaestus-prod.aet.cit.tum.de`.
- Confirm the current PostgreSQL + Keycloak backup schedule and retention with AET ops. Fill in the specific retention row in §13.
- Confirm which LLM providers are actually enabled in production and whether Zero Data Retention has been negotiated with any of them. Adjust §13 accordingly.
- Confirm that the per-job LLM proxy and sandbox network posture described in §5 of the pre-screen are unchanged.
