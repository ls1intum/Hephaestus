---
id: vt-dsms
title: DSMS Verzeichnis von Verarbeitungstätigkeiten (VVT)
description: Copy-paste answers for the DSMS follow-up questionnaire.
---

*Last updated: 2026-04-19.*

Copy-paste ready. Ordered to match the DSMS "Create new PA" form and follow-up questionnaire. Submit at: [https://dsms.datenschutz.tum.de/](https://dsms.datenschutz.tum.de/).

---

## Step 1 — "Create a new processing activity"

### Title

```text
Hephaestus – Practice-Aware Guidance Platform for Software Projects
```

### Description and Purpose (min. 200 characters)

```text
Hephaestus is a practice-aware guidance platform for software projects, operated by the Research Group for Applied Education Technologies (AET, Prof. Krusche) at the Technical University of Munich. It supports project-based software-engineering teaching at TUM and the software-development work of AET research projects. For each Project (a workspace and its configured Git repositories), the platform observes repository Events, detects workspace-defined Practices in the Artifacts produced by Contributors (pull/merge requests, issues, reviews, comments), records immutable advisory Findings, and delivers adaptive Guidance back to the Contributor. Optional features include dashboards, workspace engagement and recognition signals (leaderboards, leagues, achievements), a conversational guidance assistant, and automated practice reviews that call an external LLM provider configured per workspace. Authentication is federated through Keycloak (GitHub OAuth + gitlab.lrz.de OpenID Connect). Personal data is processed only to provide the service, ensure reliable and secure operation, and troubleshoot incidents.
```

Length check: at least 200 characters.

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

```text
Responsible unit:   Research Group for Applied Education Technologies (AET)
                    TUM School of Computation, Information and Technology
                    Department of Computer Science
                    Boltzmannstraße 3, 85748 Garching bei München

Head of unit:       Prof. Dr. Stephan Krusche
Hephaestus support: GitHub Issues at https://github.com/ls1intum/Hephaestus/issues
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

**Allocation of data-protection duties pursuant to Art. 26(2) Satz 1 GDPR:**

- **TUM/AET** is responsible for information duties under Art. 13/14 GDPR (via the privacy statement), platform-level TOMs under Art. 32 GDPR, breach notification under Art. 33/34 GDPR, the DPIA posture under Art. 35 GDPR, and this VVT under Art. 30 GDPR. TUM/AET is the single point of contact for data-subject rights.
- **The workspace administrator** is responsible for ensuring that the source-system authorisation (GitHub App installation, GitHub personal-access token, or gitlab.lrz.de personal-access token plus webhook shared secret) is in place and has been obtained lawfully, that Contributors of the workspace have been informed that their repository Artifacts are being ingested into Hephaestus, that any LLM provider credentials supplied by their own institution are backed by a processing agreement with that provider at the institution's level, and for the substantive scoping of Practices, recognition features, Slack routing and auto-trigger in their workspace.

The essence of the Art. 26(2) Satz 2 arrangement is made available to data subjects via §3.2 of the privacy statement. TUM/AET is the single point of contact for data-subject rights; data subjects may nevertheless also address the workspace administrator directly for workspace-specific questions.

### 3. Processors (Art. 28 GDPR) — each with AVV status

See `05-avv-checklist.md` for the full table. In summary, engaged processors (ordered internal → platform-wide → per-workspace) are:

- **AET operations (self-hosted Sentry on `sentry.ase.in.tum.de`)** — platform-wide error telemetry. In-house; TUM-internal framework.
- **AET operations (TUM SMTP relay via the TUM mail infrastructure)** — email delivery for Keycloak account lifecycle (verification, password reset). In-house; TUM-internal framework.
- **GitHub, Inc. / Microsoft Corporation** — identity provider (OAuth) and source-system API (pull requests, issues, reviews, commits synchronised on behalf of the controller via the workspace-configured GitHub App installation or access token). AVV in place at TUM/AET level.
- **OpenAI, L.P.** — LLM provider for workspaces configured to use it. AVV at TUM/AET level for the TUM-operated tenancy; AVV at the workspace administrator's institution level when that institution supplies the API credentials (shared-responsibility model, §3.2).
- **Microsoft Corporation (Azure OpenAI Service)** — LLM provider for workspaces configured to use it. Region-configurable; EU-region deployments process within the EU. AVV as above.
- **Anthropic, PBC** — LLM provider for workspaces configured to use it. AVV as above.
- **Salesforce, Inc. / Slack Technologies, LLC** — workspace notifications and engagement digests when the workspace administrator has enabled Slack. AVV in place at TUM/AET level.
- **PostHog Inc.** — product-analytics processor. Engaged only when `POSTHOG_ENABLED=true` is set for the deployment and the individual Contributor has not withdrawn their research-participation consent. AVV in place at TUM/AET level when engaged.

The **Leibniz-Rechenzentrum (LRZ) der Bayerischen Akademie der Wissenschaften** is **not** an Art. 28 processor; gitlab.lrz.de runs on LRZ infrastructure under a public-body cooperation framework (Art. 16 Abs. 1 Satz 2 BayHIG in conjunction with the BAdW-Satzung). LRZ and TUM each act as separate controllers (Art. 4(7) GDPR) for the data each body processes on its own infrastructure; their purposes and means are not jointly determined (Art. 26 GDPR also does not apply). See `05-avv-checklist.md` for the full EDPB 07/2020 analysis.

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
7. Operate the service reliably and securely (server logs, backups, self-hosted error telemetry) and troubleshoot incidents.
8. Improve the platform through consent-gated product analytics where `POSTHOG_ENABLED=true` for the deployment and the Contributor has not withdrawn their research-participation consent.

### 6. Name of IT system / procedure

```text
Hephaestus (https://hephaestus.aet.cit.tum.de)
Stack:
  - Browser SPA:        React 19 / Vite / TanStack Router (TypeScript)
  - Application server: Spring Boot 3.5 / Java 21 / PostgreSQL
  - Intelligence:       Python FastAPI for AI-assisted features
  - Identity:           Self-hosted Keycloak (GitHub OAuth + gitlab.lrz.de OIDC)
  - Review sandbox:     Isolated Docker containers with per-job LLM proxy
  - Reverse proxy:      Traefik v3 / Let's Encrypt
  - Error telemetry:    Self-hosted Sentry at sentry.ase.in.tum.de (AET-operated)
  - Product analytics:  PostHog, off by default, research-consent gated when on
  - Deployment:         Docker Compose on AET servers at TUM
Source (MIT): github.com/ls1intum/Hephaestus
```

### 7. Legal basis (cite GDPR article + national norm)

| Processing | Legal basis |
|---|---|
| Core service (authentication, repository sync, practice detection, guidance) for TUM Contributors | **Art. 6(1)(e) GDPR** i.V.m. **Art. 4 Satz 1 BayHIG** and **Art. 25 Abs. 1 BayDSG** (public-interest task: teaching and operation of university IT services) |
| Core service for non-TUM Contributors (external open-source contributors, partner-university members) | **Art. 6(1)(b) GDPR** (performance of the service the Contributor requested by signing in) |
| AI-assisted features (guidance assistant, practice review) | Same as the row above; Contributors may object under **Art. 21 GDPR** via the "AI review comments" profile toggle (stops future transmissions; does not by itself delete previously generated Findings) |
| Server logs & reverse-proxy logs | **Art. 6(1)(e) GDPR** i.V.m. **Art. 4 Satz 1 BayHIG**, **Art. 25 Abs. 1 BayDSG**, **Art. 8 BayDiG** (operation and security of a university IT service) |
| Error telemetry (self-hosted Sentry) | **Art. 6(1)(e) GDPR** i.V.m. **Art. 4 Satz 1 BayHIG**, **Art. 25 Abs. 1 BayDSG**, **Art. 8 BayDiG** (security and operability of the service; Sentry runs on AET infrastructure and is not shared with third parties) |
| Product analytics (PostHog) when `POSTHOG_ENABLED=true` | **Art. 6(1)(a) GDPR** (consent; implemented as the "Participate in research" toggle in profile settings; the toggle defaults to active only while PostHog is disabled platform-wide, and any analytics event is suppressed once the Contributor deactivates it — see Art. 7(3) GDPR withdrawal) |
| Keycloak session cookies and theme-preference localStorage | **§ 25 Abs. 2 Nr. 2 TDDDG** (technisch unbedingt erforderlich für einen vom Nutzer ausdrücklich gewünschten Telemediendienst) i.V.m. **Art. 6(1)(e) GDPR** |

### 8. Categories of data subjects (Art. 30(1)(c))

Tick in DSMS:

- Students (TUM)
- Employees (TUM) — lecturers, tutors, AET staff, workspace administrators
- Students (extern) — from partner universities contributing to shared projects
- Other Website Visitors — unauthenticated visitors of public pages (imprint, privacy)

### 9. Categories of personal data

- **Identity & authentication (Keycloak):** external user ID at the identity provider (GitHub user ID or `sub` claim from gitlab.lrz.de), username / login, email, full name, avatar URL, profile URL.
- **Development activity (synchronised from source systems):** pull/merge requests, issues, code reviews, review comments, commit metadata, repository collaborator and team-membership metadata, and profile information of authors of these artifacts.
- **Account settings & recognition:** notification preferences, UI display options, workspace memberships and roles, leaderboard rank, league assignment, achievement progress, the "AI review comments" Art. 21 objection switch, the "Participate in research" research-consent switch.
- **AI-assisted features:** guidance-assistant messages + AI-generated responses, conversation threads, feedback (helpful / not helpful), practice-review findings (verdict, severity, evidence, reasoning), guidance text delivered back to the Contributor.
- **Server logs (14-day cap):** IP address, timestamp, HTTP method, URL, status code, bytes transferred, user-agent, referrer.
- **Error telemetry (self-hosted Sentry):** stack trace, breadcrumbs, browser environment, Keycloak user ID and IP of the affected Contributor (`sendDefaultPii: true`).
- **Product analytics (PostHog — only when deployment-enabled and Contributor-consented):** Keycloak user ID, page-view events, feature-usage events, platform/browser metadata, IP address.
- **Browser-side storage:** Keycloak session cookies; theme preference in localStorage. No identifying data in localStorage.

### 10. Special categories (Art. 9 / Art. 10)

**None.** Hephaestus does not process health, biometric, genetic, racial, religious, political, trade-union, sex-life, sexual-orientation, or criminal-conviction data. Contributors are warned in the privacy statement not to enter third-party personal data into commits, reviews, or practice-review diffs.

### 11. Categories of recipients (Art. 30(1)(d))

- **Internal:** AET administrators / developers (operation, maintenance, support); workspace members (see workspace-level Findings and dashboards as described in §6 of the privacy statement).
- **Internal (AET-operated processors):** self-hosted Sentry on `sentry.ase.in.tum.de` (error telemetry, always on); TUM SMTP relay (Keycloak account lifecycle emails).
- **External (Art. 28 processors), as configured per workspace:** GitHub / Microsoft; the LLM provider configured for the workspace (OpenAI / Azure OpenAI / Anthropic); Slack (when enabled).
- **External (Art. 28 processors), when deployment-enabled:** PostHog (only when `POSTHOG_ENABLED=true` for the deployment and the Contributor has not withdrawn research-participation consent).
- **External (separate controllers, not Art. 28):** Leibniz-Rechenzentrum der BAdW for gitlab.lrz.de integration.
- **No sale, no advertising recipients, no brokers.**

### 12. Third-country transfers (Art. 30(1)(e))

All U.S.-based recipients are certified under the EU–U.S. Data Privacy Framework (DPF); the primary safeguard is the Commission adequacy decision under Art. 45(3) GDPR in relation to the DPF (Commission Implementing Decision of 10 July 2023 on the EU-U.S. Data Privacy Framework). Standard Contractual Clauses under Art. 46(2)(c) GDPR pursuant to Commission Implementing Decision (EU) 2021/914 — **Module 2 (controller → processor)** — are contracted as a fall-back for any processing not covered by the recipient's DPF certification. DPF certification must be re-verified against the U.S. Department of Commerce list before each annual VVT refresh.

- **GitHub, Inc. / Microsoft Corporation** — DPF-certified (active; to be re-verified). Azure OpenAI in a European region processes data within the EU.
- **OpenAI, L.P.** — DPF-certified (active; to be re-verified). SCCs Module 2 as fall-back.
- **Anthropic, PBC** — DPF-certified (active; to be re-verified). SCCs Module 2 as fall-back.
- **Salesforce, Inc. (Slack)** — DPF-certified (active; to be re-verified). SCCs Module 2 as fall-back.
- **PostHog Inc.** — DPF-certified (active; to be re-verified). SCCs Module 2 as fall-back. Engaged only under the preconditions in §11.

### 13. Retention periods per data category (Art. 30(1)(f))

| Category | Retention |
|---|---|
| Identity data in Keycloak + Hephaestus DB | Account lifetime; removed on user-triggered account deletion |
| Development activity synchronised from GitHub / gitlab.lrz.de | For as long as the repository and workspace are configured; source-side content on GitHub / gitlab.lrz.de is not affected by deletions inside Hephaestus |
| Account settings, notification prefs, recognition signals, consent/objection switches | Account lifetime |
| Guidance-assistant conversations | Account lifetime; deletable on request |
| Practice-review Findings | Workspace lifetime; deletable on request |
| LLM-provider-side prompts | Up to 30 days (enterprise default abuse-monitoring); shorter where Zero Data Retention has been negotiated per workspace |
| Server access logs (app server + reverse proxy) | **Hard 14-day maximum** enforced by `logrotate` (host-level, rotated daily with `rotate 14`) and the Docker `json-file` log driver (`max-size=10m`, `max-file=14`). Longer only where strictly necessary for an ongoing security incident, then deleted at closure |
| Self-hosted Sentry events | 90 days at the event level (Sentry default); project-level override documented in AET ops |
| PostHog events (when deployment-enabled) | 7 years (PostHog default); no TUM-level override requested at the current scope |
| Backups — PostgreSQL | Daily full backup, 30-day rolling retention (AET ops baseline, pg_dump + nightly rsync to AET backup host) |
| Backups — Keycloak realm | Daily JSON realm export, 30-day rolling retention |
| Backups — gitlab.lrz.de (LRZ-side, for deleted content) | LRZ retains its own backup window of up to 6 months for content already removed on the LRZ side, independent of Hephaestus |

### 14. Technical and Organizational Measures (Art. 30(1)(g) + Art. 32)

See `04-toms.md`. Paste into the DSMS TOMs field or upload as an attachment.

### 15. Information-duty fulfilled (Art. 13/14)

Privacy statement at: [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy)
Imprint at: [https://hephaestus.aet.cit.tum.de/imprint](https://hephaestus.aet.cit.tum.de/imprint)

Both are versioned in the repository under `webapp/public/legal/profiles/tumaet/`.

### 16. Automated decision-making / profiling (Art. 22)

**No Art. 22 decisions.** AI-assisted features produce advisory Findings and Guidance only. They are not consumed by any automated grading, assessment, HR, or access-control pipeline operated by Hephaestus. A human instructor acting on information from Hephaestus dashboards remains accountable for that decision under their own process, outside Hephaestus.

### 17. DPIA pre-check (Art. 35 GDPR)

See `02-dsfa-prescreen.md`. Conclusion: **DPIA-light posture.** The AI-assisted feature surface is on the "elevated risk" side of the BayLfD innovative-technology criterion; mitigations in §5 of the pre-screen (no-training enterprise API terms, per-job LLM proxy, Art. 21 objection switch, shared-responsibility disclosure, 14-day log cap) replace a full DPIA at the current scope. The pre-screen lists the trigger conditions under which a full DPIA must be opened.

### 18. Personalrat involvement (Art. 75 BayPVG)

**Not triggered.** Hephaestus does not monitor TUM staff performance or behaviour. Staff who use the platform as mentors, lecturers, or administrators do so on the same footing as any other Contributor. Leaderboards and league signals are student-facing engagement features, opt-in per workspace, and not consumed by any HR process.

### 19. IT-Sicherheitsformular (TUM wiki)

Not applicable as a separate upload; Hephaestus is self-hosted by AET on TUM infrastructure under the AET operational security baseline. See `04-toms.md` for the per-category detail.

### 20. Source of data

- Directly from the data subject: profile settings, notification preferences, guidance-assistant messages, feedback, consent/objection switches.
- From federated identity providers (GitHub, gitlab.lrz.de) via OAuth / OIDC during sign-in.
- From source-system APIs (GitHub, gitlab.lrz.de) via the workspace-configured installation / access token: repository Events and Artifacts authored by the Contributor.
- From the underlying HTTP connection: IP address, user-agent (standard web-server logging, 14-day cap).
- From the Contributor's browser: error telemetry to the self-hosted Sentry, and — where enabled and consented — PostHog product-analytics events.

### 21. Data-subject rights contact

Primary: [beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de) (TUM DPO). Operational queries: [ls1.admin@in.tum.de](mailto:ls1.admin@in.tum.de) (AET admins). Data subjects may additionally address the workspace administrator directly for questions about the workspace-specific configuration (§3.2 of the privacy statement).

### 22. Attachments to upload in DSMS

- Privacy statement snapshot (export of [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy), PDF).
- `02-dsfa-prescreen.md` (DPIA pre-check / DPIA-light documentation).
- `04-toms.md` (TOMs).
- `05-avv-checklist.md` (Art. 28 checklist; every external and internal recipient and the status of its DPA).

### 23. Status

Set to **Submitted** after all fields are filled and attachments uploaded.
