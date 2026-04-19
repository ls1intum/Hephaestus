---
id: avv-checklist
title: Art. 28 Processor Checklist
description: Per-processor AVV status for the TUM-operated Hephaestus deployment.
---

# Hephaestus — Art. 28 Processor Checklist

Documents every external entity that might qualify as a processor (Art. 28 GDPR) and the status of the required contract (Auftragsverarbeitungsvertrag, AVV).

## Summary

Unlike Apollon, Hephaestus has a non-trivial processor chain: GitHub (identity + source-system API), the per-workspace LLM provider (OpenAI / Azure OpenAI / Anthropic), Slack (when enabled), and the TUM SMTP relay. The LRZ (gitlab.lrz.de) is **not** a processor; it is a separate controller under the § 16 BayHIG public-body cooperation framework.

## Detailed check

| Component | Role | AVV required? | Status |
|---|---|---|---|
| AET servers at TUM (container host) | Own infrastructure | No (Art. 4(7) GDPR — controller's own equipment) | — |
| PostgreSQL (in-house container) | Data store for Hephaestus application data | No (self-hosted) | — |
| Keycloak (in-house container) | Identity and session management | No (self-hosted) | — |
| Spring Boot application server (in-house container) | Application logic | No (self-hosted) | — |
| Intelligence service / FastAPI (in-house container) | AI-assisted features orchestration | No (self-hosted) | — |
| Traefik v3 reverse proxy (in-house container) | TLS termination, routing | No (self-hosted) | — |
| Let's Encrypt ACME endpoint | Domain-validation certificates | No — Let's Encrypt receives no personal data; it only checks control over the domain | — |
| **GitHub, Inc. / Microsoft Corporation** | Identity provider (OAuth) and source-system API (pull requests, issues, reviews, commits synchronised on behalf of the controller) | **Yes** | DPA in place at TUM/AET level; DPF-certified; SCCs as fall-back |
| **OpenAI, L.P.** | LLM provider for workspaces configured to use it | **Yes, when engaged** | DPA at TUM/AET level for the TUM-operated tenancy; at the workspace administrator's institution level when that institution supplies credentials (shared-responsibility model); enterprise API no-training terms; DPF-certified; SCCs as fall-back |
| **Microsoft Corporation (Azure OpenAI Service)** | LLM provider for workspaces configured to use it (region-configurable; EU deployments process in-region) | **Yes, when engaged** | DPA as above; Microsoft DPF certification; SCCs as fall-back |
| **Anthropic, PBC** | LLM provider for workspaces configured to use it | **Yes, when engaged** | DPA as above; DPF-certified; SCCs as fall-back |
| **Salesforce, Inc. / Slack Technologies, LLC** | Workspace notifications and engagement digests when Slack is enabled by the workspace administrator | **Yes, when engaged** | DPA in place at TUM/AET level; DPF-certified; SCCs as fall-back |
| **TUM SMTP relay** (TUM mail infrastructure) | Email delivery (Keycloak verification, password reset, optional notification emails) | No external DPA needed | In-house; TUM-internal framework |
| **Leibniz-Rechenzentrum der BAdW (gitlab.lrz.de)** | Source system and OIDC identity provider | **Not Art. 28** | Separate controller under § 16 Abs. 1 Satz 2 BayHIG + BAdW-Satzung (public-body cooperation); LRZ applies its own TOMs on its own infrastructure |
| GitHub / GHCR (for CI, image hosting) | Stores Docker images and CI logs; does not receive end-user personal data of the Hephaestus service | No | Covered by TUM's general agreements with GitHub Enterprise — employment-related processing of AET staff, not end-user data |

## Why the LRZ relationship is not Art. 28

An Art. 28 processor is engaged to process personal data on behalf of the controller, under the controller's documented instructions. For gitlab.lrz.de, the LRZ operates its own GitLab instance for the entire Bavarian academic-computing community under its own mandate as the regular IT service provider for TUM and LMU under § 16 Abs. 1 Satz 2 BayHIG in conjunction with the BAdW-Satzung. The LRZ decides its own purposes and means for operating that platform; Hephaestus interacts with it as a consumer of its public-body service, not as an engager of processor services. LRZ and TUM each act as separate controllers (Art. 4(7) GDPR) for the data each body processes on its own infrastructure. This framing is aligned with the BayLfD's published guidance on public-body cooperation among Bavarian state institutions.

Art. 26 GDPR (joint controllership) does not apply either, because LRZ and TUM do not jointly determine the purposes and means of the processing — each body serves its own distinct purpose on its own infrastructure.

## Why the workspace administrator is not an Art. 28 processor

Workspace administrators (TUM chairs, lecturers, research-group leads) are **joint controllers** with TUM/AET for the six workspace-configurable decisions documented in §3.2 of the privacy statement (repository set, LLM provider + credentials, Slack routing, leaderboard enablement, practice catalog, auto-trigger). Art. 26 GDPR applies. The essence of the Art. 26(2) Satz 2 arrangement is made available to data subjects via the privacy statement itself; TUM/AET is the single point of contact for data-subject rights; data subjects may additionally address the workspace administrator directly for workspace-specific questions.

## Follow-up if the VT surface changes

If a future Hephaestus deployment adds any of the following, amend this file **and** the VT **and** the privacy notice before deploying:

- A new LLM provider — requires adding the provider to §11 of the VT, to `05-avv-checklist.md`, and to §6 of the privacy statement.
- A new identity provider beyond GitHub and gitlab.lrz.de — amend §4.1 of the privacy statement and §9 of the VT.
- Any analytics or additional error telemetry (Sentry, PostHog, Matomo) — would make the vendor a processor; requires a DPA and a matching §6 entry in the privacy statement.
- Any external storage (S3, CDN) — would require an AVV.
- Any third-party font, script, image, or embed — would expose the user's IP to the provider.
- Any widening of the practice-review sandbox network posture beyond the per-job LLM proxy — triggers a re-audit under §5 of `02-dsfa-prescreen.md`.
