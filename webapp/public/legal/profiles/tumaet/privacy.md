Privacy Statement for Hephaestus in accordance with Art. 13 and 14 GDPR.

*Last updated: 2026-04-19.*

The Technical University of Munich (TUM), through the Research Group for Applied Education Technologies (AET), operates the Hephaestus platform ("Hephaestus" or "the platform"). Personal data collected through Hephaestus is processed in accordance with the General Data Protection Regulation (GDPR / DSGVO), the Bavarian Data Protection Act (BayDSG), the Bavarian Higher Education Innovation Act (BayHIG), and the German Telecommunications Digital Services Data Protection Act (TDDDG).

## 1. Controller

The controller within the meaning of Art. 4(7) GDPR is:

Technical University of Munich  
Arcisstraße 21, 80333 Munich, Germany  
represented by its President, Prof. Dr. Thomas F. Hofmann  
Telephone: +49 (0)89 289-01  
Email: [poststelle@tum.de](mailto:poststelle@tum.de)

Operational responsibility for Hephaestus lies with:

Research Group for Applied Education Technologies (AET)  
Prof. Dr. Stephan Krusche  
TUM School of Computation, Information and Technology  
Department of Computer Science  
Boltzmannstraße 3, 85748 Garching bei München, Germany  
Email: [ls1.admin@in.tum.de](mailto:ls1.admin@in.tum.de)

## 2. Data Protection Officer

Technical University of Munich — Office of the Data Protection Officer  
Arcisstraße 21, 80333 Munich, Germany  
Email: [beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de)  
Website: [www.datenschutz.tum.de](https://www.datenschutz.tum.de/en/datenschutz/der-datenschutzbeauftragte/)

## 3. Purpose and Scope

Hephaestus is a **practice-aware guidance platform for software projects**, used to support project-based software-engineering teaching at TUM and the software-development work of AET research projects. For each *Project* (a workspace plus its configured Git repositories), the platform observes repository *Events*, detects workspace-defined *Practices* in the *Artifacts* produced by *Contributors* (pull/merge requests, issues, reviews, comments), records immutable *Findings* (verdict, severity, evidence, reasoning), and delivers adaptive *Guidance* back to the Contributor — the Observe → Detect → Guide → Grow pipeline. The platform may additionally surface dashboards, engagement & recognition features (leaderboards, leagues, achievements — workspace-opt-in), workspace notifications, a conversational guidance assistant, and automated practice reviews of Contributor pull/merge requests.

**Findings are advisory.** The platform does *not* produce an automated decision with legal or similarly significant effects within the meaning of Art. 22 GDPR. Guidance is delivered to the Contributor, and per-Artifact Findings plus per-practice rollups are visible to other members of the same workspace — including workspace administrators, team leads, and teaching staff — as informational dashboards. A human instructor or team lead may therefore become aware of a Contributor's Findings when consulting these dashboards; any consideration they then give them (for example, when providing individual feedback outside Hephaestus) is their own judgement under their own process and is not a decision taken by Hephaestus within the meaning of Art. 22 GDPR. Hephaestus itself does not automate grading, assessment, HR processes, or any access-control decision, and no Finding is consumed by an automated decision pipeline operated by Hephaestus.

Use of the platform requires authentication through a self-hosted identity-management system (Keycloak) via one of the federated identity providers enabled for your workspace. Only the identity providers that have been enabled for a given workspace are available on the login screen. The identity providers enabled at the platform level are listed in §4.1.

### 3.1 Connected source systems

Hephaestus synchronises repository *Events* and *Artifacts* from source-control platforms configured by the workspace administrator. The source systems integrated at the platform level are:

- **GitHub** (github.com, operated by GitHub, Inc. / Microsoft Corp.) — used both as a federated identity provider (OAuth) and as a source system for workspaces whose repositories are hosted on GitHub.
- **gitlab.lrz.de** — the GitLab instance operated by the **Leibniz-Rechenzentrum (LRZ) der Bayerischen Akademie der Wissenschaften** (Boltzmannstraße 1, 85748 Garching), used by TUM courses and research groups that host their repositories on the LRZ infrastructure. Hephaestus integrates gitlab.lrz.de both as a federated identity provider (OpenID Connect) and as a source system. The LRZ is the scientific computing centre of the BAdW and is the regular IT service provider for TUM and LMU under Art. 16 Abs. 1 Satz 2 BayHIG in conjunction with the BAdW-Satzung. For data-protection questions concerning the LRZ infrastructure, the LRZ Data Protection Officer is reachable at [datenschutz@lrz.de](mailto:datenschutz@lrz.de). The LRZ GitLab terms of use cover instructional use and non-commercial academic research, which is the purpose for which Hephaestus processes gitlab.lrz.de data. LRZ and TUM each act as separate controllers (Art. 4(7) GDPR) for the data each body processes on its own infrastructure; the relationship is a public-body cooperation under Art. 16 Abs. 1 Satz 2 BayHIG and the BAdW-Satzung and is not an Art. 28 GDPR commission; Art. 26 GDPR does not apply because the two bodies do not jointly determine the purposes and means.

### 3.2 Workspace-configurable integrations (shared-responsibility model)

Hephaestus operates a **shared-responsibility model** in which TUM/AET operates the platform layer and each workspace administrator configures a limited set of external integrations for their own workspace:

- **TUM / AET** operates the Hephaestus platform itself — the orchestrator, the PostgreSQL database, the Keycloak realm, the practice-review sandbox, access control, backups, incident response, and the present privacy statement.
- **The workspace administrator** (typically a TUM chair, lecturer, or research-group lead) decides, for their own workspace, (i) which Git repositories are synchronised into the workspace (the data population), (ii) which LLM provider and which credentials are used by the workspace's AI-assisted features, (iii) whether workspace notifications are routed to Slack (engaging Salesforce/Slack as a further processor), (iv) whether leaderboards, leagues and achievements are enabled for peer visibility, (v) the workspace's Practice catalog — the set of practices against which Artifacts are evaluated, and (vi) whether practice reviews are auto-triggered on new pull/merge requests.

In respect of these six decisions, TUM and the workspace administrator jointly determine the purposes and means within the meaning of Art. 26 GDPR. The present section 3.2 constitutes the essence of the joint-controllership arrangement made available to the data subject pursuant to Art. 26(2) Satz 2 GDPR.

**Allocation of data-protection duties pursuant to Art. 26(2) Satz 1 GDPR:**

- **TUM/AET** is responsible for information duties under Art. 13/14 GDPR (via this privacy statement), platform-level TOMs under Art. 32 GDPR, breach notification under Art. 33/34 GDPR, the DPIA posture under Art. 35 GDPR, and the record of processing under Art. 30 GDPR. TUM/AET is the single point of contact for data-subject rights (see §2 and §9).
- **The workspace administrator** is responsible for ensuring that the source-system authorisation (GitHub App installation, GitHub personal-access token, or gitlab.lrz.de personal-access token plus webhook shared secret) has been obtained lawfully, that Contributors of the workspace have been informed that their repository Artifacts are being ingested into Hephaestus, that any LLM-provider credentials supplied by their own institution are backed by a processing agreement maintained at that institution's level (with TUM not being the controller for that onward transmission), and for the substantive scoping of Practices, recognition features, Slack routing, and auto-trigger in their workspace.

Contributors may nevertheless also address the workspace administrator directly, in particular for questions about the workspace-specific configuration.

## 4. Data We Collect and Process

The sections below describe each category of personal data we process, the purpose, the legal basis, and the applicable retention period.

### 4.1 Identity and authentication data

To use Hephaestus, you must authenticate using an external identity provider enabled for your workspace. The identity providers federated into the Hephaestus Keycloak realm at the platform level are:

- **GitHub** (github.com) — OAuth 2.0.
- **gitlab.lrz.de** — OpenID Connect, operated by the Leibniz-Rechenzentrum der BAdW. The OIDC flow against gitlab.lrz.de is operated under the same public-body cooperation framing described in §3.1 and is not an Art. 28 GDPR commission.

Depending on the identity provider you choose, the following data is transmitted from the provider to Keycloak and subsequently to Hephaestus:

- External user identifier at the identity provider (GitHub user ID or the `sub` claim returned by gitlab.lrz.de)
- Username / login at the identity provider
- Email address (as provided by the identity provider)
- Full name (as provided by the identity provider)
- Profile avatar URL and profile URL (where the provider exposes them)

You can link additional federated identity providers to a single Hephaestus account via your profile settings and unlink them at any time, provided at least one active link remains.

**Purpose:** user identification, session management, and access control.

**Legal basis:** Art. 6(1)(e) GDPR in conjunction with Art. 4 Satz 1 BayHIG and Art. 25 Abs. 1 BayDSG (performance of a task carried out in the public interest — teaching and operation of university IT services). For users who are not members of TUM (e.g. external open-source contributors), processing is carried out on the basis of Art. 6(1)(b) GDPR (performance of a contract / provision of the service you requested by logging in).

**Retention:** identity data is retained for as long as your account exists. You can delete your account at any time in your profile settings; upon deletion, identity data is removed from Keycloak and the platform database (see Section 9).

### 4.2 Development activity data

Hephaestus synchronizes development activity from Git repositories configured in a workspace by the workspace administrator. Data is retrieved via the GitHub API or the gitlab.lrz.de GitLab API (see §3.1) using an app installation or access token configured by the platform administrator. Only repositories explicitly added to a workspace are synchronized. The synchronized data includes:

- Pull/merge requests (title, description, state, author, reviewers, labels, timestamps, linked commits)
- Issues (title, description, state, assignees, labels, timestamps)
- Code reviews (review body, state, author)
- Review comments (comment body, author, file path, line position)
- Commit metadata (SHA, author login, committer, message, timestamp)
- Repository collaborator and team-membership metadata
- Profile information of authors of the above (username, display name, avatar URL, profile URL)

**Purpose:** practice detection and adaptive guidance over project artifacts — the core functionality of the platform.

**Legal basis:** Art. 6(1)(e) GDPR in conjunction with Art. 4 Satz 1 BayHIG and Art. 25 Abs. 1 BayDSG (public-task processing for the purpose of teaching and the administration of university IT services). The data is already available on the originating Git platform to the same audience. For non-TUM contributors the basis is Art. 6(1)(b) GDPR.

**Workspace administrator's responsibility:** the workspace administrator is responsible for ensuring that the relevant authorisation (GitHub App installation, GitHub personal-access token, or gitlab.lrz.de personal-access token and webhook shared secret) is in place on the source-system side, and that Contributors in the workspace are informed that their repository Artifacts are being ingested into Hephaestus.

**Retention:** synchronized data is retained for as long as the corresponding workspace and repository are configured on the platform. When a repository or workspace is removed, the associated data is deleted. Source-side content on GitHub or gitlab.lrz.de is *not* deleted by this action and remains subject to the source platform's own retention rules.

### 4.3 Account settings, engagement & recognition, and consent switches

Hephaestus stores account settings and derives engagement & recognition signals from your development activity, including:

- User preferences (notification preferences, UI display options)
- Workspace memberships and roles
- Recognition signals: rank on the weekly leaderboard, league assignment, achievement progress (workspace-opt-in)
- An **"AI review comments"** switch — your Art. 21 GDPR objection switch against AI-assisted processing of your Artifacts (see §4.4).

Recognition signals are visible to other members of the same workspace and update as activity syncs. Summaries (username and rank) may be shared via configured notification channels (e.g. a workspace Slack digest) when the workspace administrator enables them.

**Purpose:** personalised guidance surfaces, workspace engagement & recognition, notifications, and honouring your Art. 21 GDPR objection switch.

**Legal basis:** Art. 6(1)(e) GDPR in conjunction with Art. 4 Satz 1 BayHIG and Art. 25 Abs. 1 BayDSG for the workspace engagement & recognition features and for workspace-level Slack digests that the workspace administrator has enabled.

**Retention:** for as long as your account exists. You may delete your account at any time (see Section 9).

### 4.4 AI-assisted features (guidance assistant, practice review)

When AI-assisted features are active for your workspace, the following data is processed:

- Your chat messages with the *guidance assistant* and the AI-generated responses
- Conversation history (threads) and your feedback on AI responses (helpful / not helpful)
- For *practice reviews*: the diff and relevant context of the pull/merge request under review, including author username and commit metadata
- The resulting immutable *Findings* (verdict, severity, evidence, reasoning) and any *Guidance* text delivered back to the Contributor

To generate responses, the platform transmits your messages — together with relevant context such as code excerpts or review data — to the LLM provider configured for your workspace (see §6). Only the messages and relevant context are transmitted; your full user profile is not. For practice reviews, source code is additionally executed inside isolated, resource-limited Docker containers on AET infrastructure to derive structural signals before the LLM call. By default these sandboxes run on a Docker `--internal` network with no outbound connectivity except a tightly scoped, per-job LLM proxy operated by AET (DNS resolution is disabled; the proxy authenticates calls with a per-job token). Content transmitted to the LLM provider is governed by the provider's enterprise API terms (which prohibit the use of transmitted data for model training), by Contributors' general duty not to commit secrets to a tracked repository, and by the workspace administrator's responsibility to scope the repositories routed into Hephaestus accordingly.

**Workspace-configurable LLM provider:** the choice of LLM provider (OpenAI, Microsoft Azure OpenAI, or Anthropic) and the associated API credentials are configured per workspace by the workspace administrator. Where the workspace administrator's institution supplies the API credentials, that institution is the controller for the onward transmission to the LLM provider and is responsible for maintaining any Art. 28 GDPR processing agreement with the provider (see §3.2).

**Purpose:** adaptive practice guidance for Contributors engaged in a Project (practice detection + guidance generation).

**Legal basis:** For TUM Contributors, Art. 6(1)(e) GDPR in conjunction with Art. 4 Satz 1 BayHIG and Art. 25 Abs. 1 BayDSG — AI-assisted practice guidance is part of the teaching function for which Hephaestus is operated, and the feature is integrated as a default part of that service. Contributors have the right to **object at any time (Art. 21 GDPR)** to the AI-assisted processing of their Artifacts by disabling "AI review comments" in their profile settings and/or by not using the guidance assistant; upon objection, the platform stops sending the Contributor's new Artifacts to the LLM provider and stops generating new Findings about them. For non-TUM Contributors the basis is Art. 6(1)(b) GDPR (service requested by signing in).

**Retention:** guidance-assistant conversations are retained for as long as your account exists and can be deleted on request; practice-review Findings are retained for the lifetime of the workspace and may be deleted earlier on request. An Art. 21 objection stops future processing but does not by itself delete previously generated Findings — a separate erasure request (see §9) is required for that.

**Automated decision-making (Art. 22 GDPR):** AI-assisted features produce Findings and Guidance only. Findings and Guidance are *not themselves* automated decisions with legal or similarly significant effects within the meaning of Art. 22 GDPR, and the platform does not consume them in any automated grading, assessment, HR, or access-control pipeline operated by Hephaestus. Workspace members — including peers, workspace administrators, team leads, and teaching staff — can view per-Artifact Findings and per-practice rollups for the Contributors in their workspace as informational dashboards; these dashboards inform human judgement but are not themselves a decision within the meaning of Art. 22 GDPR. A human instructor acting on information from these dashboards (for example, when giving individual feedback) remains accountable for that decision under their own process, outside Hephaestus.

### 4.5 Server log data

Every time the platform is accessed, our web servers and reverse proxy automatically collect and temporarily store the following information in log files:

- IP address of the requesting computer
- Date and time of the request
- URL and HTTP method of the request
- HTTP status code of the response
- Volume of data transferred
- Browser type, version, and operating system (if transmitted by the browser)
- Referring URL (if transmitted by the browser)

**Purpose:** operation and security of the platform, including the detection and prevention of attacks.

**Legal basis:** Art. 6(1)(e) GDPR in conjunction with Art. 4 Satz 1 BayHIG, Art. 25 Abs. 1 BayDSG and Art. 8 BayDiG (security of the university IT system as part of the public task).

**Retention:** log rotation is enforced jointly by `logrotate` on the container host (daily rotation with `rotate 14`, maximum one file per day) and by the Docker `json-file` log driver (`max-size=10m`, `max-file=14`), yielding a **hard maximum of 14 days** under normal operating conditions. Logs containing an IP address are retained beyond this window only where strictly necessary to investigate a specific, ongoing security incident, and are then deleted as soon as the incident is closed. Logs are not merged with other data sources and are only accessible to AET operators.

## 5. Cookies and Browser-Side Storage

Hephaestus uses only technically necessary browser-side storage:

- **Keycloak session cookies:** strictly necessary to maintain your login session.
- **Theme preference (`theme` in local storage):** remembers your light/dark mode. Contains no personal data.

**Legal basis:** § 25 Abs. 2 Nr. 2 TDDDG (strictly necessary storage) in conjunction with Art. 6(1)(e) GDPR, Art. 4 Satz 1 BayHIG and Art. 25 Abs. 1 BayDSG (public-task operation of the platform; the theme preference contains no personal data). No consent-requiring analytics or tracking cookies are used.

## 6. Recipients and Third-Party Services

Your personal data may be accessible to the following recipients in connection with the purposes described in Section 4:

- **AET team members** — platform administrators and developers (operation, maintenance, support).
- **Workspace members** — other members of workspaces you belong to can see your username, avatar, the *Findings* attached to pull/merge requests you authored within that workspace, per-practice *Finding* rollups about you on workspace dashboards, and workspace engagement and recognition signals (rank, league, achievements) where those features are enabled for the workspace. Workspace administrators, team leads, and teaching staff in the same workspace see the same information for the Contributors they work with.
- **Leibniz-Rechenzentrum (LRZ) der BAdW** — when a workspace synchronises from gitlab.lrz.de, or when a Contributor signs in via the gitlab.lrz.de OIDC identity provider, personal data is exchanged with LRZ infrastructure in Garching. LRZ and TUM each act as separate controllers (Art. 4(7) GDPR) for the data each body processes on its own infrastructure; the relationship is a public-body cooperation under Art. 16 Abs. 1 Satz 2 BayHIG and the BAdW-Satzung and is not an Art. 28 GDPR commission (§3.1). LRZ privacy information: [doku.lrz.de/display/PUBLIC/Datenschutzerklaerung](https://doku.lrz.de/display/PUBLIC/Datenschutzerklaerung). LRZ GitLab terms of use: [doku.lrz.de/gitlab-nutzungsrichtlinien-10746021.html](https://doku.lrz.de/gitlab-nutzungsrichtlinien-10746021.html).

The platform uses the following processors and third-party services. Some of them are only engaged when the workspace administrator of your workspace has configured the corresponding feature (see §3.2 on the shared-responsibility model):

- **GitHub, Inc. / Microsoft Corporation** — identity-provider (OAuth) for Contributors logging in with GitHub; repository-data synchronization for GitHub-hosted workspaces. [Privacy policy](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
- **LLM provider per workspace** — the AI-assisted features of each workspace call one of the following providers, as configured by the workspace administrator: **OpenAI, L.P.** (enterprise API, no-training terms — [privacy policy](https://openai.com/policies/privacy-policy)); **Microsoft Corporation (Azure OpenAI Service)**, region-configurable, EU-region deployments process within the EU — [privacy policy](https://privacy.microsoft.com/privacystatement); **Anthropic, PBC** (Claude API under enterprise no-training terms) — [privacy policy](https://www.anthropic.com/legal/privacy). Only the provider configured for your workspace receives your AI-assisted interactions.
- **Salesforce, Inc. / Slack Technologies, LLC** — workspace notifications and engagement/recognition digests *only when your workspace administrator has enabled Slack for the workspace*. [Privacy policy](https://slack.com/trust/privacy/privacy-policy).
- **SMTP infrastructure operated by AET** via the TUM mail relay — email delivery for Keycloak account-verification and password-reset messages. No transfer outside TUM/AET.

Where the relationship with one of the above is subject to Art. 28 GDPR, a data-processing agreement (Auftragsverarbeitungsvertrag / DPA) is in place at the level of TUM/AET for the TUM-operated integrations (GitHub, the TUM-operated LLM tenancy, Slack, and the TUM SMTP relay); for LLM-provider transmissions that use credentials supplied by the workspace administrator's institution, the DPA is maintained at that institution's level (§3.2, §4.4). The relationship with the LRZ is *not* an Art. 28 commission but a public-body cooperation under Art. 16 Abs. 1 Satz 2 BayHIG and the BAdW-Satzung.

## 7. Third-Country Transfers

The core platform infrastructure (application server, database, Keycloak) is operated on servers managed by AET at TUM within Germany. The gitlab.lrz.de source system runs on LRZ infrastructure in Garching, Germany — no third-country transfer. The following third-party services are based in the United States; transfers to them are protected by the safeguards listed below. Where the recipient is certified under the EU–U.S. Data Privacy Framework (DPF), the primary safeguard is the Commission adequacy decision under Art. 45(3) GDPR (Commission Implementing Decision of 10 July 2023 on the EU-U.S. Data Privacy Framework). For any processing not covered by the recipient's DPF certification, Standard Contractual Clauses under Art. 46(2)(c) GDPR pursuant to Commission Implementing Decision (EU) 2021/914 — **Module 2 (controller-to-processor)** — serve as a fall-back safeguard:

- **GitHub, Inc. / Microsoft Corporation** — DPF-certified; SCCs Module 2 as fall-back. Azure OpenAI in a European region processes data within the EU.
- **OpenAI, L.P.** — DPF-certified; SCCs Module 2 as fall-back.
- **Anthropic, PBC** — DPF-certified; SCCs Module 2 as fall-back (engaged only for workspaces whose administrator has selected Anthropic as LLM provider).
- **Salesforce, Inc. (Slack)** — DPF-certified; SCCs Module 2 as fall-back.

No personal data is transferred to third countries without appropriate safeguards in accordance with Chapter V GDPR.

## 8. SSL/TLS Encryption

Hephaestus uses SSL/TLS encryption for all connections. You can recognize an encrypted connection by the "https://" prefix and the lock icon in your browser.

## 9. Your Rights

Under the GDPR, you have the following rights with respect to your personal data:

- **Access (Art. 15 GDPR):** request information about the personal data we store about you.
- **Rectification (Art. 16 GDPR):** request the correction of inaccurate data.
- **Erasure (Art. 17 GDPR):** request the deletion of your personal data, subject to legal retention obligations. You can delete your account via the platform settings; doing so removes your profile, preferences, guidance-assistant conversations, Findings attached to you, and the link to your federated identity providers, from Keycloak and the Hephaestus database. Content you originated on GitHub or gitlab.lrz.de (commits, pull/merge requests, issues) is *not* deleted on those source systems by this action — you must delete it there separately. Prompts transmitted to the LLM provider prior to your deletion remain subject to that provider's own retention window (up to 30 days for default enterprise abuse-monitoring, shorter where Zero Data Retention has been negotiated with the provider for the workspace) before being deleted by the provider; after that window they are not associated with your deleted account. The LRZ retains its own backup/visibility window of up to six months for deleted gitlab.lrz.de content on its infrastructure, independent of Hephaestus.
- **Restriction of processing (Art. 18 GDPR):** request restriction under certain circumstances.
- **Data portability (Art. 20 GDPR):** receive the data you have provided in a structured, machine-readable format.
- **Object (Art. 21 GDPR):** object to processing based on Art. 6(1)(e) GDPR. If you object, TUM/AET will cease the corresponding processing unless compelling legitimate grounds for the processing can be demonstrated.

To exercise any of these rights, please contact [ls1.admin@in.tum.de](mailto:ls1.admin@in.tum.de) or the TUM Data Protection Officer (see Section 2).

## 10. Right to Lodge a Complaint

Without prejudice to any other remedy, you have the right to lodge a complaint with a supervisory authority (Art. 77 GDPR). The competent supervisory authority for TUM is:

Der Bayerische Landesbeauftragte für den Datenschutz (BayLfD)  
Wagmüllerstraße 18, 80538 Munich, Germany  
Telephone: +49 (0)89 212672-0  
Email: [poststelle@datenschutz-bayern.de](mailto:poststelle@datenschutz-bayern.de)  
Website: [https://www.datenschutz-bayern.de](https://www.datenschutz-bayern.de)

## 11. Obligation to Provide Data

The provision of personal data is neither legally nor contractually required. However, use of the platform is not possible without authentication through a federated identity provider configured for your workspace (see §4.1), which requires the transmission of the identity data described there. If your workspace has AI-assisted features active, the transmission of your Artifacts to the LLM provider configured for that workspace (§4.4) is part of the platform's teaching function; you may object at any time (Art. 21 GDPR) and the platform will stop sending your new Artifacts to the LLM provider.

## 12. Changes to This Privacy Statement

TUM may update this privacy statement from time to time to reflect changes in data processing or legal requirements. The current version is available at [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy).

## 13. Email Security

Email addresses used to contact TUM/AET about the platform are used only for the corresponding correspondence. Standard email transmission may have security vulnerabilities; complete protection of data from access by third parties during email transmission cannot be guaranteed.
