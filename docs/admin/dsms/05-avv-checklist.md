---
id: avv-checklist
title: Art. 28 Processor Checklist
description: Per-processor AVV status for the TUM-operated Hephaestus deployment.
---

*Last updated: 2026-04-19.*

Documents every entity that might qualify as a processor (Art. 28 GDPR) and the status of the required contract (Auftragsverarbeitungsvertrag, AVV). Internal (AET-operated) recipients and the LRZ public-body counterpart are listed for completeness so the record is self-contained.

## Summary

Hephaestus engages a non-trivial processor chain: AET-operated self-hosted Sentry (always on), TUM SMTP relay (always on), GitHub (identity + source-system API), the per-workspace LLM provider (OpenAI / Azure OpenAI / Anthropic), Slack (per-workspace opt-in), and PostHog (off by default; opt-in deployment flag gated by per-Contributor research-participation consent). The LRZ (gitlab.lrz.de) is **not** a processor; it is a separate controller under the Art. 16 Abs. 1 Satz 2 BayHIG public-body cooperation framework.

## Detailed check

| Component | Role | AVV required? | Status |
|---|---|---|---|
| AET servers at TUM (container host) | Own infrastructure | No (Art. 4(7) GDPR — controller's own equipment) | — |
| PostgreSQL (in-house container) | Data store for Hephaestus application data | No (self-hosted) | — |
| Keycloak (in-house container) | Identity and session management | No (self-hosted) | — |
| Spring Boot application server (in-house container) | Application logic | No (self-hosted) | — |
| Intelligence service / FastAPI (in-house container) | AI-assisted features orchestration | No (self-hosted) | — |
| Traefik v3 reverse proxy (in-house container) | TLS termination, routing | No (self-hosted) | — |
| **Self-hosted Sentry** at `sentry.ase.in.tum.de` | Error telemetry (stack traces, breadcrumbs, Keycloak user ID, IP — `sendDefaultPii: true`) | No external DPA — in-house; TUM-internal framework | Always on; 90-day event retention (Sentry default). Access restricted to AET operators. |
| **TUM SMTP relay** (TUM mail infrastructure) | Email delivery (Keycloak verification, password reset) | No external DPA needed | In-house; TUM-internal framework |
| Let's Encrypt ACME endpoint | Domain-validation certificates | No — Let's Encrypt receives no personal data; it only checks control over the domain | — |
| **GitHub, Inc. / Microsoft Corporation** | Identity provider (OAuth) and source-system API (pull/merge requests, issues, reviews, commits synchronised on behalf of the controller) | **Yes** | DPA in place at TUM/AET level; DPF-certified (active; re-verify annually); SCCs Module 2 as fall-back |
| **OpenAI, L.P.** | LLM provider for workspaces configured to use it | **Yes, when engaged** | DPA at TUM/AET level for the TUM-operated tenancy; at the workspace administrator's institution level when that institution supplies credentials (shared-responsibility model, privacy §3.2); enterprise API no-training terms; DPF-certified (active; re-verify annually); SCCs Module 2 as fall-back |
| **Microsoft Corporation (Azure OpenAI Service)** | LLM provider for workspaces configured to use it (region-configurable; EU deployments process in-region) | **Yes, when engaged** | DPA as above; Microsoft DPF certification; SCCs Module 2 as fall-back |
| **Anthropic, PBC** | LLM provider for workspaces configured to use it | **Yes, when engaged** | DPA as above; DPF-certified (active; re-verify annually); SCCs Module 2 as fall-back |
| **Salesforce, Inc. / Slack Technologies, LLC** | Workspace notifications and engagement digests when Slack is enabled by the workspace administrator | **Yes, when engaged** | DPA in place at TUM/AET level; DPF-certified (active; re-verify annually); SCCs Module 2 as fall-back |
| **PostHog Inc.** | Product analytics (page/feature events, Keycloak user ID, IP) — engaged only when `POSTHOG_ENABLED=true` for the deployment and the Contributor has not withdrawn research-participation consent | **Yes, when engaged** | DPA in place at TUM/AET level; DPF-certified (active; re-verify annually); SCCs Module 2 as fall-back. Off by default platform-wide. |
| **Leibniz-Rechenzentrum (LRZ) der BAdW (gitlab.lrz.de)** | Source system and OIDC identity provider | **Not Art. 28** | Separate controller under Art. 16 Abs. 1 Satz 2 BayHIG + BAdW-Satzung (public-body cooperation); LRZ applies its own TOMs on its own infrastructure |
| GitHub / GHCR (for CI, image hosting) | Stores Docker images and CI logs; does not receive end-user personal data of the Hephaestus service | No (controller-to-controller on AET-staff data; end-user Hephaestus data is not transferred) | Covered by TUM's general agreements with GitHub Enterprise |

## Why the LRZ relationship is not Art. 28

An Art. 28 processor is engaged to process personal data on behalf of the controller, under the controller's documented instructions. Under the EDPB Guidelines 07/2020 on the concepts of controller and processor in the GDPR (adopted 7 July 2021), Section 1.1 (§§ 14–33), the decisive criterion is who determines the "essential means" of the processing — i.e. the purposes, which data, which subjects, how long, what access.

For gitlab.lrz.de the LRZ operates its own GitLab instance for the entire Bavarian academic-computing community under its own mandate as the regular IT service provider for TUM and LMU (Art. 16 Abs. 1 Satz 2 BayHIG in conjunction with the BAdW-Satzung). LRZ determines the service's purpose, the onboarding rules, the retention windows, the backup regime, the TOMs, and the terms of use. TUM does not instruct LRZ on how to operate gitlab.lrz.de; TUM consumes the service as one eligible public body among many under the BayHIG mandate. This is the textbook scenario of **two separate controllers each processing on its own infrastructure for its own purpose**, and is incompatible with Art. 28 processor status under EDPB 07/2020.

Art. 26 GDPR (joint controllership) is equally absent: EDPB 07/2020 § 50–65 require a **joint determination of purposes and means**, which is not present here — TUM and LRZ each pursue their own distinct purpose, and neither determines the other's processing parameters. The relationship is a public-body cooperation, consistent with the BayLfD's published guidance on public-body cooperation among Bavarian state institutions.

## Why the workspace administrator is not an Art. 28 processor

Workspace administrators (TUM chairs, lecturers, research-group leads) are **joint controllers** with TUM/AET for the six workspace-configurable decisions documented in §3.2 of the privacy statement (repository set, LLM provider + credentials, Slack routing, leaderboard enablement, practice catalog, auto-trigger). Art. 26 GDPR applies. The essence of the Art. 26(2) arrangement and the allocation of data-protection duties under Art. 26(2) Satz 1 are made available to data subjects via the privacy statement itself; TUM/AET is the single point of contact for data-subject rights; data subjects may additionally address the workspace administrator directly for workspace-specific questions.

## Follow-up if the VVT surface changes

If a future Hephaestus deployment adds any of the following, amend this file **and** the VVT **and** the privacy notice before deploying:

- A new LLM provider — requires adding the provider to §11 of the VVT, to this checklist, and to §6 of the privacy statement.
- A new identity provider beyond GitHub and gitlab.lrz.de — amend §4.1 of the privacy statement and §9 of the VVT.
- Replacement of the self-hosted Sentry by a SaaS error-tracking service — converts an internal recipient into an Art. 28 U.S. processor; requires a DPA, a §6 privacy-statement entry, and a DPIA re-assessment.
- Any additional analytics beyond PostHog (Matomo, etc.) — requires a DPA and a matching §6 entry.
- Any external storage (S3, CDN) — would require an AVV.
- Any third-party font, script, image, or embed — would expose the user's IP to the provider.
- Any widening of the practice-review sandbox network posture beyond the per-job LLM proxy — triggers a re-audit under §5 of `02-dsfa-prescreen.md`.
