---
id: toms
title: Technical and Organizational Measures
description: Art. 32 GDPR TOMs for the TUM-operated Hephaestus deployment.
---

*Last updated: 2026-04-19.*

Documents the measures taken pursuant to Art. 32 GDPR. Structured along the categories of the BayLfD / DSK TOM-Leitfaden so every Muss-Liste category is addressed on its own line.

## 1. Confidentiality (Art. 32(1)(b))

### 1.1 Access control to premises (Zutrittskontrolle)

- Hephaestus runs on AET-managed servers on TUM premises. Physical access is restricted to AET staff and TUM facility management.
- Data-centre-like conditions (power, cooling, fire detection) are provided by the TUM / AET facility.

### 1.2 System access (Zugangskontrolle)

- The VMs are reachable via SSH only from authorised operators using SSH key pairs. Password authentication is disabled.
- The GitHub Actions deployment pipeline uses per-environment GitHub secrets for SSH keys and deploy credentials, rotated on demand.
- End-user access to the Hephaestus UI and API is gated by self-hosted Keycloak with federated identity providers (GitHub OAuth, gitlab.lrz.de OpenID Connect). Keycloak issues short-lived access tokens; refresh tokens are rotated.
- Administrative roles inside Hephaestus (workspace administrator, AET admin) are granted by explicit role assignment; role changes are auditable.

### 1.3 Data access (Zugriffskontrolle)

- Only AET operators with container-host SSH access can read the PostgreSQL database and the Keycloak realm directly. Neither is exposed to the public internet.
- The reverse proxy (Traefik) exposes only the routes required for the browser SPA, the authenticated API, the WebSocket gateway, and Keycloak endpoints. Every other path returns 404.
- Authorisation inside Hephaestus is enforced at the application layer: a Contributor only sees Artifacts, Findings, and dashboards for workspaces they are a member of; workspace administrators see the full workspace content; AET administrators see cross-workspace state for operational purposes only.
- Practice-review sandboxes run as non-root inside isolated Docker containers on an internal Docker `--internal` network with no outbound connectivity, except a tightly scoped, per-job LLM proxy (DNS disabled, per-job token authentication).

### 1.4 Separation of processing purposes (Trennungskontrolle)

- PostgreSQL schemas and row-level scoping ensure workspace data is not cross-accessible.
- Each workspace's LLM-provider configuration is isolated; credentials are stored encrypted at rest and are not shared across workspaces.
- Keycloak realm is dedicated to Hephaestus; no co-tenancy with unrelated TUM services.

## 2. Integrity (Art. 32(1)(b))

### 2.1 Transfer control (Weitergabekontrolle)

- All external traffic is TLS-terminated at Traefik (Let's Encrypt). HTTP redirects to HTTPS with HSTS `max-age=63072000; includeSubdomains; preload`.
- Internal service-to-service traffic stays within the Docker network (application server, intelligence service, PostgreSQL, Keycloak, sandbox orchestrator).
- WebSocket traffic uses `wss://` (TLS) in production.
- Outbound transfers to processors (GitHub API, LLM providers, Slack) are TLS-only. Transfer safeguards for U.S. recipients: EU-U.S. DPF adequacy decision + SCCs Module 2 fall-back (see `05-avv-checklist.md`).

### 2.2 Input control (Eingabekontrolle)

- Git is the authoritative source for all application code; changes are traceable via signed commits + PR history.
- The GitHub Actions deployment pipeline pushes images tagged by the source commit SHA; cosign-signed images are the only artifacts allowed in production.
- All API writes are authenticated and authorised; audit records are produced for high-value actions (workspace creation, role assignment, LLM-provider credential changes, account deletion).

## 3. Availability and resilience (Art. 32(1)(b), (c))

### 3.1 Availability (Verfügbarkeitskontrolle)

- Containers restart automatically on failure (`restart: unless-stopped`).
- Health checks run every 5–30 s per service; Docker marks unhealthy containers.
- TLS-certificate renewal through Let's Encrypt ACME is automated.

### 3.2 Resilience (Belastbarkeit)

- Resource limits are set per container.
- LLM-provider calls have bounded timeouts and retry policies; practice reviews degrade gracefully when an LLM provider is unreachable.
- The sandbox enforces per-job concurrency ceilings and CPU / memory limits.
- Rate-limits on the ingress reverse proxy protect unauthenticated endpoints.

### 3.3 Recoverability (Wiederherstellbarkeit)

- **PostgreSQL:** daily `pg_dump` with a 30-day rolling retention on the AET backup host; full restore procedure documented in AET runbooks.
- **Keycloak:** daily realm export (JSON) with a 30-day rolling retention on the AET backup host.
- **Application secrets:** held in the AET secret store and replicated to the backup host with the same 30-day retention.
- Quarterly restore drill verifies the backup set.

## 4. Testability, regular evaluation, and measure effectiveness (Art. 32(1)(d))

### 4.1 Evaluation procedures (Verfahren zur Evaluation)

- Privacy page and imprint source are versioned in the repository under `webapp/public/legal/profiles/tumaet/`. Every change is reviewable in PRs.
- Unit, architecture, integration, and end-to-end tests run on CI (GitHub Actions). The legal-page render path is covered by dedicated tests that exercise the three-layer cascade, the XSS guardrail on operator-supplied Markdown, and the scheme-relative URL rejection.
- Dependency scanning runs on CI (npm audit + Dependabot + Renovate).
- Docker images are signed with cosign and pushed to GHCR. Image provenance is verifiable.
- The DSMS submission package (this directory) is re-reviewed once per year and after any material change to the processing surface (see `README.md`).

## 5. Pseudonymisation, encryption, and confidentiality at rest (Art. 32(1)(a))

- User accounts are linked to federated identity-provider identifiers (GitHub user ID, gitlab.lrz.de `sub`). The platform does not derive further identifiers of its own beyond surrogate primary keys.
- Network traffic is TLS-encrypted end-to-end (browser → Traefik → internal services; Hephaestus → LLM provider / Slack over HTTPS).
- PostgreSQL and Keycloak data at rest are protected by disk-level protections on the host. Workspace-level secrets (LLM API keys, Slack tokens) are stored encrypted with a platform-level secret key held only on the application server.

## 6. Deletion (Löschkontrolle)

- **Account deletion** (self-service, profile settings): removes the Keycloak account, the Hephaestus profile, preferences, guidance-assistant conversations, the Art. 21 objection switch, and the Contributor's identity link to their Findings. Source-side GitHub / gitlab.lrz.de content is *not* deleted by this action — that must be done on the source system separately.
- **Workspace deletion / repository removal** triggers deletion of synchronised Artifacts for that workspace or repository.
- **Server logs** are rotated under a hard 14-day maximum enforced by `logrotate` (host-level, `rotate 14`) and the Docker `json-file` log driver (`max-size=10m`, `max-file=14`).
- **LLM-provider-side retention** is bounded by enterprise no-training terms; default 30-day retention, shorter where Zero Data Retention has been negotiated per workspace. After the provider's window, prompts are not associated with the deleted account.
- **Backups** follow the 30-day PostgreSQL / Keycloak rolling retention. On restore after an account deletion, the application-level deletion log is re-applied to prevent re-materialising deleted data.

## 7. Organisational measures

- Operators are TUM / AET employees or authorised contributors; they act under Art. 4 Satz 1 BayHIG public-duty mandates and TUM-internal security policies.
- The DPO ([beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de)) reviews incidents. Data-subject requests follow the process in `docs/admin/legal-pages` + the live `/privacy` page.
- Incidents are recorded in GitHub Issues against the `ls1intum/Hephaestus` repository and, if they affect personal data, reported to the TUM DPO within 72 h (Art. 33 GDPR).
- Workspace administrators are instructed on the shared-responsibility model (§3.2 of the privacy statement) before the workspace is provisioned; the workspace-configurable surface (LLM provider, Slack, leaderboards, practice catalog, auto-trigger) is not enabled by default.
- Operator onboarding includes a written Art. 28 / Art. 29 confidentiality undertaking at the AET-internal level.

## 8. Sub-processors and internal processors

| Component | Role | AVV / framework | Default state |
|---|---|---|---|
| AET operations (TUM SMTP relay) | Email delivery (Keycloak lifecycle) | In-house; TUM-internal framework | Always on |
| GitHub, Inc. / Microsoft Corporation | Identity provider (OAuth) and source-system API | AVV at TUM/AET level | Always on for GitHub-federated workspaces |
| OpenAI, L.P. | LLM provider (per workspace) | AVV at TUM/AET level for TUM-operated tenancy; at workspace administrator's institution when that institution supplies credentials | Per-workspace, opt-in |
| Microsoft Corporation (Azure OpenAI Service) | LLM provider (per workspace) | AVV as above | Per-workspace, opt-in |
| Anthropic, PBC | LLM provider (per workspace) | AVV as above | Per-workspace, opt-in |
| Salesforce, Inc. / Slack Technologies, LLC | Workspace notifications (when enabled by the workspace admin) | AVV at TUM/AET level | Per-workspace, opt-in |
| Leibniz-Rechenzentrum der BAdW (gitlab.lrz.de) | Source system and OIDC identity provider | **Not Art. 28** — separate controller under Art. 16 Abs. 1 Satz 2 BayHIG + BAdW-Satzung (public-body cooperation; EDPB 07/2020 § 22) | Per-workspace, opt-in |
