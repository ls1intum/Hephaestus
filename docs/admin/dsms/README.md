---
id: dsms
sidebar_position: 3
title: DSMS Submission Package
description: Art. 30 GDPR / Verarbeitungstätigkeit record for the TUM-operated Hephaestus deployment.
---

# Hephaestus — DSMS Submission Package

This directory is the complete record-of-processing (Art. 30 GDPR / "Verarbeitungstätigkeit") package for the TUM-operated Hephaestus deployment at `https://hephaestus.aet.cit.tum.de`. Submit it through the TUM DSMS at **[https://dsms.datenschutz.tum.de/](https://dsms.datenschutz.tum.de/)** (reachable from MWN / eduVPN with TUM login).

## Scope

Hephaestus is a practice-aware guidance platform for software projects, operated by the Research Group for Applied Education Technologies (AET, Prof. Krusche). The platform federates identities through Keycloak (GitHub OAuth + gitlab.lrz.de OIDC), synchronises repository activity from GitHub and gitlab.lrz.de, and optionally engages external processors configured per workspace: LLM providers (OpenAI / Microsoft Azure OpenAI / Anthropic), Slack, and the TUM SMTP relay. Server access logs contain IP addresses and are rotated with a hard 14-day maximum.

## Contents

| File | Purpose |
|---|---|
| [`README.md`](./README.md) | This file |
| [`SUBMISSION-GUIDE.md`](./SUBMISSION-GUIDE.md) | Ordered submission procedure |
| [`02-dsfa-prescreen.md`](./02-dsfa-prescreen.md) | DPIA pre-check (Art. 35 GDPR) — concludes DPIA-light is warranted and may upgrade if the AI surface grows |
| [`03-vt-dsms.md`](./03-vt-dsms.md) | Copy-paste VT answers for the DSMS form |
| [`04-toms.md`](./04-toms.md) | Technical and Organizational Measures (Art. 32 GDPR) |
| [`05-avv-checklist.md`](./05-avv-checklist.md) | Art. 28 processor checklist — lists every external recipient and its AVV status |

The live imprint and privacy pages are served at:

- [https://hephaestus.aet.cit.tum.de/imprint](https://hephaestus.aet.cit.tum.de/imprint)
- [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy)

Markdown source lives under [`webapp/public/legal/profiles/tumaet/`](https://github.com/ls1intum/Hephaestus/tree/main/webapp/public/legal/profiles/tumaet).

## Why Hephaestus is broader than Apollon

| Concern | Apollon | Helios | Hephaestus |
|---|---|---|---|
| Authentication | none | GitHub OAuth | Keycloak (GitHub + gitlab.lrz.de) |
| Third-party processors | none | GitHub, Sentry (self-hosted) | GitHub, LLM providers (OpenAI / Azure OpenAI / Anthropic), Slack, SMTP |
| Special category data | none | none | none |
| AI / profiling | none | none | LLM-assisted guidance and automated practice review |
| Retention of user content | 120-day Redis TTL | account lifetime | account lifetime |
| DPIA required? | No | No | **DPIA-light (may upgrade)** |

Hephaestus carries the largest processing surface of the three ls1intum services. The VT is more involved, the AVV checklist is non-empty, and the DPIA pre-check concludes that a DPIA-light is warranted; a full DPIA is not required at the current scope but should be reconsidered if the AI surface expands (e.g. additional LLM providers, new data categories, deeper profiling).

## Annual refresh

Re-review the VT once per year:

- Has the deployed stack changed? (new processor, new data category, new retention window?)
- Has the platform added a new LLM provider or a new source system? Any of these requires an amended VT, an amended privacy page, and a new row in the AVV checklist.
- Are the retention figures in `03-vt-dsms.md` still matching the deployed config (server-log rotation, LLM provider retention windows, Redis / Postgres / Keycloak backup schedules)?
- Has the scope of AI-assisted features grown to the point that the DPIA pre-screen in `02-dsfa-prescreen.md` needs to upgrade to a full DPIA under the BayLfD template?

## Emergency — DSB rejection

The DSB may comment in DSMS. Typical follow-ups and responses:

- *"Rechtsgrundlage zu konkretisieren"* → §7 of the VT cites Art. 6(1)(e) GDPR + Art. 4 BayHIG + Art. 25 BayDSG for TUM members, and Art. 6(1)(b) GDPR for non-TUM Contributors. Point them there.
- *"Löschkonzept fehlt"* → §13 of the VT lists retention per category, including account deletion flows and the hard 14-day server-log cap.
- *"AVV fehlt für X"* → see [`05-avv-checklist.md`](./05-avv-checklist.md) for the per-processor DPA status.
- *"DSFA erforderlich"* → upgrade [`02-dsfa-prescreen.md`](./02-dsfa-prescreen.md) to the BayLfD DPIA template; the pre-screen already captures the residual-risk structure a full DPIA would elaborate.

Export DSB comments, update the relevant file, and re-submit.

## Contacts

- TUM DPO: [beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de)
- DSMS tool support: [support@datenschutz.tum.de](mailto:support@datenschutz.tum.de)
- TUM DSMS overview: [https://www.datenschutz.tum.de/datenschutz/verarbeitungstaetigkeit/](https://www.datenschutz.tum.de/datenschutz/verarbeitungstaetigkeit/)
- Hephaestus operational contact: [ls1.admin@in.tum.de](mailto:ls1.admin@in.tum.de)
