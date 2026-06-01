# Hephaestus — Record of Processing Activities (Art. 30 GDPR)

_Last updated: 2026-05-07._

This file is the Art. 30 record for the TUM-operated Hephaestus deployment at https://hephaestus.aet.cit.tum.de. Each section maps to a single Art. 30 element. Fenced code blocks are paste-ready into the corresponding TUM DSMS form field at https://dsms.datenschutz.tum.de/; everything outside the fences is contextual.

## Identifier

- Title: `Hephaestus – Practice-Aware Feedback for Software Projects`
- Tags: `Webdienst`, `Lehre`, `Forschungsprojekt`
- Joint Controller: tick (workspace administrators are joint controllers under Art. 26 GDPR — see "Legal basis" below).
- Relevant for Subject Rights Request (SRR): tick.
- Responsible department: TUM School of Computation, Information and Technology.
- Associated TUM Org identifier: `TUS1322`.
- DPIA pre-screen: see [`dpia-prescreen.md`](./dpia-prescreen.md) — outcome: no full DPIA required at the current scope; documented mitigations remain in place.

## Controller and contact (Art. 30(1)(a))

```text
Prof. Dr. Stephan Krusche, Head of AET
Research Group for Applied Education Technologies
TUM School of Computation, Information and Technology
Department of Computer Science
Boltzmannstraße 3, 85748 Garching bei München, Germany

Operational technical contact: ls1.admin@in.tum.de
```

DSMS responsible person: Stephan Krusche (krusche@tum.de). Felix Dietrich (felixtj.dietrich@tum.de) added as additional responsible person for edit access.

## Purpose and description (Art. 30(1)(b))

```text
Hephaestus is a self-hosted web platform operated by AET on TUM infrastructure at https://hephaestus.aet.cit.tum.de. Its purpose is to support project-based software-engineering teaching at TUM and the development work of AET research projects by giving each contributor feedback on their collaborative engineering work: for example, whether a pull request is small enough to review well, or whether a review reply addresses the question raised.

A workspace administrator connects one or more Git repositories from github.com or gitlab.lrz.de. Hephaestus then synchronises the pull/merge requests, issues, code reviews, review comments, and commit metadata authored in those repositories. The platform processes activity authored in the connected repositories, whether or not the author has signed in to Hephaestus.

The synchronised activity is analysed against a set of practices configured by the workspace administrator to produce findings on each contributor's activity. Some of these judgements require reading and understanding natural-language text, such as the meaning of a code comment or the substance of a review reply. For those, the analysis uses an external LLM provider chosen by the administrator for the workspace. The automated practice review of a pull/merge request forwards the diff and surrounding discussion to the provider and posts the AI-generated finding as a comment on the pull/merge request. The conversational mentor is an in-app chat where contributors can ask follow-up questions; their messages and the surrounding context are forwarded to the same provider.

Contributors who sign in with their GitHub or LRZ-GitLab account get a personal dashboard summarising their findings and activity, access to the conversational mentor, and their account preferences. Sign-in adds the federated user identifier, username, display name, email, and avatar URL to what Hephaestus holds about that contributor. Workspace administrators can additionally enable a leaderboard, leagues, and achievements based on workspace activity (all off by default), and Slack notifications for workspace events.

These workspace-level configuration choices are made by the workspace administrator and TUM/AET as joint controllers under Art. 26 GDPR (the choices are enumerated in "Legal basis" below). Hephaestus is built around the contributor's own development: findings serve the contributor and give the workspace administrator a way to deliver targeted feedback during the project. Findings are advisory and contestable; the platform makes no automated decisions within the meaning of Art. 22 GDPR and feeds no grading, assessment, HR, or access-control pipeline. Contributors can disable AI-assisted feedback at any time through the in-app "AI review comments" toggle (Art. 21 GDPR), and rate individual findings via a helpful / not-helpful control.
```

## Data subjects (Art. 30(1)(c))

Tick in DSMS:

- Students (TUM)
- Students (extern)
- Employees (TUM)
- Employees (extern)
- Other Website Visitors

## Categories of personal data

Tick in DSMS: Name(s), Contact details: email, Image data, Indicators of Behaviour, IP address, Social network data, User IDs and Passwords. Do **not** tick "Examination and academic performance" — practice findings are advisory, not graded.

```text
Repository-activity artefacts authored by the contributor in the connected Git repositories (pull/merge requests, issues, code reviews, review comments, commit metadata) and AI guidance-assistant conversations.
```

No special categories (Art. 9(1) GDPR) and no Art. 10 GDPR data are processed. Contributors are warned in the privacy statement not to enter third-party personal data into commits, reviews, or practice-review diffs.

## Recipients (Art. 30(1)(d))

```text
External processors engaged by TUM/AET as controller. AVVs are in place at TUM/AET level for the AET-pool processors. Where a workspace administrator configures a different LLM endpoint (see below), the AVV is at that administrator's institution.

- GitHub, Inc. (USA) — identity provider (OAuth) and source-system API for connected repositories on github.com.

- An external LLM provider, chosen per workspace by the workspace administrator from any OpenAI-API-compatible HTTPS endpoint (configured by a base URL, an API token, and a model name). The choice is a joint-controller decision under Art. 26 GDPR. The TUM-operated deployment uses the Microsoft Azure OpenAI Service in an EU region under enterprise no-training terms by default. A workspace administrator may configure a different endpoint instead, such as OpenAI OpCo, LLC (with OpenAI Ireland Ltd. as the EEA contracting party), an institution-level enterprise gateway, or a self-hosted model server.

- Salesforce, Inc. / Slack Technologies, LLC (USA) — workspace notifications when Slack has been enabled for the workspace.

Separate controller (not an Art. 28 processor):

- Leibniz-Rechenzentrum (LRZ) der BAdW — operator of gitlab.lrz.de. The platform receives the contributor's identity from gitlab.lrz.de OIDC and synchronises connected gitlab.lrz.de repositories. Inter-public-body transmission under Art. 5(1) Nr. 1 BayDSG.
```

Per-processor AVV detail and the EDPB 07/2020 reasoning for the LRZ relationship are in `processor-checklist.md`. PostHog (product analytics) is bundled in the webapp image but disabled in the TUM-operated deployment; activating PostHog would engage it as an Art. 28 U.S. processor with corresponding AVV / DPF / SCC framing, and would trigger an amendment to this record.

## Third-country transfers (Art. 30(1)(e))

U.S. recipients are covered by the EU-US Data Privacy Framework (Commission Implementing Decision (EU) 2023/1795) where the recipient is on the active DPF list, with Standard Contractual Clauses Module 2 (Commission Implementing Decision (EU) 2021/914) as fall-back. The TUM-operated deployment uses Microsoft Azure OpenAI in an EU region by default, keeping that processing inside the EU.

## Storage location and retention (Art. 30(1)(f))

**Where stored**

```text
Self-hosted by AET at https://hephaestus.aet.cit.tum.de on AET-administered infrastructure at TUM. Application data — including authentication state (accounts, federated identity links, the cookie-session revocation list, and the auth-event log) — in PostgreSQL; webhook events and the practice-review job queue in NATS JetStream. Local working copies of monitored repositories may be stored on the host filesystem when practice-review code execution is enabled. Container stdout is rotated by the container runtime (Docker json-file driver, 50 MiB per file × 5 files retained per service). The application-server access log is retained for at most 14 days.

Application and authentication data reside on TUM infrastructure within the EU. AI-assisted features additionally forward code snippets and surrounding discussion to the workspace-configured LLM provider (default for the TUM-operated deployment: Microsoft Azure OpenAI in an EU region).
```

**Retention**

```text
Mixed retention by category:

- Account-bound data (the Hephaestus account, federated identity links, analytics identity): removed on user-triggered account deletion via the in-app control.
- Authentication-event log (sign-in / sign-out, token issue / refresh, impersonation begin / end; includes the source IP address and a hashed session identifier): retained for 12 months in monthly partitions, the oldest dropped automatically, as a security measure (Art. 6(1)(f) i.V.m. Art. 32 GDPR). On account deletion the account reference is detached but the event row is retained for the remainder of its window.
- Contributor profile (login, name, email, avatar) and authored repository artefacts (issues, pull/merge requests, comments, reviews) synchronised from GitHub / gitlab.lrz.de, plus AI conversations, recognition signals, and practice findings generated by the platform: retained while at least one workspace continues to track the contributor's repositories. Removed when the last workspace stops monitoring the source repository, or on operator-executed deletion against the production database on receipt of a verified erasure request.
- LLM-provider-side prompts: according to the chosen provider's terms. For the TUM-operated default (Microsoft Azure OpenAI in an EU region), within the enterprise abuse-monitoring window published in Microsoft's Azure OpenAI data-privacy documentation; eligible customers may apply for Microsoft's modified abuse monitoring (Limited Access program) to suppress prompt storage and human review.
- Application-server access log: at most 14 days; longer only for the duration of an active security incident, then deleted on closure.
- Container stdout: rotated by size by the container runtime (50 MiB × 5 files per service).
```

**Reasoning**

```text
Hephaestus is contributor-facing. Account-bound data exists to give the data subject continuity of feedback while they participate, and is removed when they leave or on a verified erasure request (Art. 5(1)(e) GDPR storage limitation). Server-side logs and container stdout are bounded to a window short enough to limit exposure and long enough to investigate security incidents under Art. 32(1) GDPR (Art. 5(1)(c) data minimisation).
```

**Deletion responsibility**

```text
Routine retention-driven deletion (logs, container stdout): handled automatically by the runtime; ops contact AET operations team, ls1.admin@in.tum.de. Subject-rights deletion: Prof. Dr. Stephan Krusche (head of AET, responsible for this PA), with technical execution by AET maintainers on receipt of a verified request through the TUM DPO at beauftragter@datenschutz.tum.de. Identity-verification procedure and Art. 12(3) GDPR response timeframe (one month, extendable by two further months for complex or numerous requests) are described in §7 of the privacy statement.
```

**Access and portability fulfilment (Art. 15, Art. 20)**

```text
Hephaestus provides a self-service data export (Art. 20): a signed-in contributor requests an export from the in-app settings (account "Danger Zone"), the platform compiles a JSON archive of the personal data it holds about that contributor — the Hephaestus account, federated identity links, workspace memberships, account preferences, and the contributor's own authentication-event history — and the contributor downloads it from the app. The archive deliberately excludes credentials and session/signing-key material. Anything outside that scope (e.g. access-log entries within the 14-day retention window, or the analytics identity where PostHog is activated) is added by AET operators on a verified Art. 15 request. Source-side content on GitHub or gitlab.lrz.de is exported by those source platforms, not by Hephaestus. Identity verification, response timeframe, and contact path are the same as for erasure.
```

**Deletion guarantee**

```text
- The Hephaestus account, federated identity links, and (where PostHog analytics is activated; off by default in the TUM-operated deployment) the corresponding analytics identity: removed by the in-app account-deletion control. Deletion immediately revokes all sessions and marks the account for deletion with a 48-hour cancellation window; after the window a scheduled sweeper hard-deletes the account-bound rows (identity links, feature flags, the session/revocation list, export artefacts), tombstones the account's contact PII, and severs the link to the git-provider activity mirror.
- Contributor profile and dependent records (workspace memberships, AI conversations, practice-finding feedback, recognition signals): removed by AET operators against the production database — by row deletion where supported by foreign-key constraints, otherwise by anonymisation that severs the link to federated identity (the records cease to be personal data within the meaning of Art. 4(1) GDPR).
- Source-side content on GitHub or gitlab.lrz.de: not modified by deletion in Hephaestus.
- Container stdout: rotated by size by the container runtime.
- Application-server access log: pruned by Tomcat's native 14-day retention.
- Off-host backups: not configured at the time of submission. Any VM-level snapshots taken by AET infrastructure operations are governed by their separate retention policy at the infrastructure layer.
```

## Technical and organisational measures (Art. 30(1)(g) + Art. 32)

```text
Pseudonymisation and encryption (Art. 32(1)(a))
- TLS-terminated at Traefik with Let's Encrypt; HTTP redirects to HTTPS with HSTS.
- Internal service-to-service traffic stays within the Docker network.
- Outbound calls to GitHub, gitlab.lrz.de, the LLM provider, and Slack are HTTPS-only.
- Federated identity links to GitHub user ID / gitlab.lrz.de `sub` minimise collected identifiers; surrogate primary keys are used internally.
- PostgreSQL data at rest relies on the host's filesystem and access-control protections; application-level at-rest encryption of the general store is not currently enabled. Workspace-level secrets (LLM API keys, Slack tokens, OAuth client secrets) and upstream OAuth tokens are encrypted with a platform-level secret key held only on the application server; the JWT signing keys are sealed under a separate system key.

Confidentiality (Art. 32(1)(b))
- SSH key-only host access; password authentication disabled.
- End-user access via Hephaestus-native auth federating to GitHub OAuth + gitlab.lrz.de OIDC; short-lived ES256 cookie-session JWTs with server-side revocation (ADR 0017).
- Workspace-scoped membership and role checks enforced at the application layer (`@PreAuthorize` and dedicated workspace-membership filters).
- Least-privilege source-system access via per-workspace GitHub App installation or scoped access token.
- Reverse proxy exposes only required routes; everything else returns 404.
- Practice-review sandbox runs as non-root inside isolated Docker containers on per-job `--internal` networks with no DNS and no general egress; the only outbound path is a per-job, token-authenticated LLM proxy. Sandbox is off by default (`SANDBOX_ENABLED=false`) and is workspace opt-in.
- Audit records produced for high-value writes (workspace creation, role assignment, LLM-provider credential changes, account deletion).

Integrity (Art. 32(1)(b))
- Git is the authoritative source of all application code; signed commits and PR review.
- Production images are pinned by sha256 digest, cosign-signed (Sigstore keyless), and provenance-attested via `actions/attest-build-provenance`; verification recipe in [Agent image digests](../agent-image-digests.md).

Availability and resilience (Art. 32(1)(b))
- Containers restart on failure (`restart: unless-stopped`); per-service health checks.
- Resource limits per container; per-job sandbox concurrency / CPU / memory ceilings.
- Bounded LLM-call timeouts; no finding is posted when an LLM provider is unreachable.
- Ingress rate limits on unauthenticated endpoints.
- TLS-certificate renewal via Let's Encrypt ACME automated.

Recovery (Art. 32(1)(c))
- No scheduled off-host backup of personal data is configured in the application repository at the time of submission. The PostgreSQL container uses a named Docker volume on the host. The authoritative copies of pull/merge-request content live on GitHub and gitlab.lrz.de. Loss of the host-local PostgreSQL volume would lose Hephaestus-specific state (workspace state, findings, practice configurations) — risk accepted at this scope, with off-host backup tracked as an open AET-ops item.

Testing and evaluation (Art. 32(1)(d))
- CI runs CodeQL (GitHub Default Setup), Trivy (filesystem and container image), TruffleHog secret detection, and Renovate dependency updates.
- Unit, integration, and end-to-end tests run on every change.

Organisational
- Operators are TUM / AET employees or authorised contributors acting under TUM-internal security policies.
- Workspace administrators are briefed on the joint-controller / shared-responsibility model (privacy §10) before workspace provisioning.
```

## Legal basis (Art. 6 GDPR + national norms)

Tick in DSMS:

- Art. 6.1a GDPR (consent) — for workspaces collecting explicit consent (e.g., the AET capstone course).
- Art. 6.1b GDPR (contract / service request) — for voluntary sign-in by non-TUM contributors.
- Art. 6.1e GDPR (public task) — for TUM/AET operation of the platform.

Do **not** tick 6.1f. Bavarian public bodies cannot rely on legitimate interest for tasks carried out in the performance of a statutory public duty (Art. 6(1) Unterabsatz 2 GDPR).

National multi-select: tick `Art. 4.1 BayDSG (Bavarian data protection act)`.

```text
TUM/AET as platform operator: Art. 6(1)(e) GDPR i.V.m. Art. 2 BayHIG (Allgemeine Aufgaben der Hochschule) and Art. 4(1) BayDSG.

Per-workspace lawful basis: workspace administrator and TUM/AET are joint controllers under Art. 26 GDPR for the workspace's processing. The administrator invokes the basis applicable to their workspace's contributors — typically Art. 6(1)(a) GDPR (consent, e.g. the AET capstone course's application phase) or Art. 6(1)(e) GDPR i.V.m. Art. 2 BayHIG (public-task activity by a TUM unit, e.g. regular courses or public open-source repositories such as ls1intum/Artemis). Administrators outside TUM cannot invoke Art. 6(1)(e) BayHIG and invoke a basis available to them (typically Art. 6(1)(a) consent, or Art. 6(1)(f) for private bodies under their own LIA).

Voluntary sign-in by non-TUM contributors to use personal features: Art. 6(1)(b) GDPR.

The Hephaestus session cookie (`__Host-HEPHAESTUS_AT`), the CSRF + OAuth-state cookies, and theme-preference localStorage: § 25 Abs. 2 Nr. 2 TDDDG (technisch unbedingt erforderlich) i.V.m. Art. 6(1)(e) GDPR.
```

## Source of data

DSMS multi-select: tick `Data received from third parties` and `Directly from the data subject`.

```text
- From GitHub and gitlab.lrz.de: identity at sign-in (GitHub OAuth, gitlab.lrz.de OIDC, federated directly by the application server); repository activity via the GitHub App installation or workspace-configured access token; webhook events delivered to the platform's /webhooks endpoint.
- Directly from the data subject: account preferences, AI-assistant messages, the Art. 21 objection switch.
- From the HTTP connection: the application server's access log records IP address, timestamp, HTTP method, URL, HTTP version, status, response size, and processing time. No User-Agent, no Referer, no cookies, no other request headers.
```

## Information duty (Art. 13)

- https://hephaestus.aet.cit.tum.de/privacy
- https://hephaestus.aet.cit.tum.de/imprint

Markdown source under `webapp/public/legal/profiles/tumaet/`.

## Other Remarks (DSMS form vendor-pool comment)

```text
Bitte folgende Auftragsverarbeiter zum AET-Pool hinzufügen, soweit noch nicht vorhanden: GitHub Inc. (USA), Microsoft Corp. (Azure OpenAI Service, USA/EU), OpenAI OpCo, LLC (USA) ggf. mit OpenAI Ireland Ltd. (Irland) als EWR-Vertragspartner, Salesforce / Slack Technologies, LLC (USA). Beschreibungen unter "Recipient Categories"; Drittlandtransfers durch das EU–US Data Privacy Framework und Standardvertragsklauseln Modul 2 (jeweils im Rahmen des einschlägigen Enterprise-AVV) abgedeckt; DPF-Status pro Empfänger vor Anbindung verifizieren.
```
