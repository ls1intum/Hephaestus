---
id: dsms
sidebar_position: 3
title: DSMS Submission Package
description: Art. 30 GDPR / Verarbeitungstätigkeit record for the TUM-operated Hephaestus deployment.
---

*Last updated: 2026-04-19.*

This directory is the complete record-of-processing (Art. 30 GDPR / "Verzeichnis von Verarbeitungstätigkeiten", VVT) package for the TUM-operated Hephaestus deployment at `https://hephaestus.aet.cit.tum.de`. Submit it through the TUM DSMS at **[https://dsms.datenschutz.tum.de/](https://dsms.datenschutz.tum.de/)** (reachable from MWN / eduVPN with TUM login).

## Scope

Hephaestus is a practice-aware guidance platform for software projects, operated by the Research Group for Applied Education Technologies (AET, Prof. Krusche). The platform federates identities through Keycloak (GitHub OAuth + gitlab.lrz.de OIDC), synchronises repository activity from GitHub and gitlab.lrz.de, and engages the following external and internal processors: LLM providers configured per workspace (OpenAI / Microsoft Azure OpenAI / Anthropic), Slack (when enabled per workspace), the TUM SMTP relay, the AET-operated self-hosted Sentry instance for error telemetry, and — only where `POSTHOG_ENABLED=true` for the deployment and the Contributor has not withdrawn their research-participation consent — PostHog product analytics. Server access logs contain IP addresses and are rotated with a hard 14-day maximum under logrotate + the Docker `json-file` log driver.

## Contents

| File | Purpose |
|---|---|
| [`README.md`](./README.md) | This file |
| [`SUBMISSION-GUIDE.md`](./SUBMISSION-GUIDE.md) | Ordered submission procedure |
| [`02-dsfa-prescreen.md`](./02-dsfa-prescreen.md) | DPIA pre-check (Art. 35 GDPR) — records the DPIA-light posture and the conditions that would require a full DPIA |
| [`03-vt-dsms.md`](./03-vt-dsms.md) | Copy-paste VVT answers for the DSMS form |
| [`04-toms.md`](./04-toms.md) | Technical and Organizational Measures (Art. 32 GDPR) |
| [`05-avv-checklist.md`](./05-avv-checklist.md) | Art. 28 processor checklist — every external and internal recipient and its AVV status |

The live imprint and privacy pages are served at:

- [https://hephaestus.aet.cit.tum.de/imprint](https://hephaestus.aet.cit.tum.de/imprint)
- [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy)

Markdown source lives under [`webapp/public/legal/profiles/tumaet/`](https://github.com/ls1intum/Hephaestus/tree/main/webapp/public/legal/profiles/tumaet).

## Summary of the processing surface

- Federated identities via Keycloak (GitHub OAuth + gitlab.lrz.de OIDC).
- Repository synchronisation from GitHub and gitlab.lrz.de into workspace-scoped datasets.
- AI-assisted guidance and automated practice review calling a workspace-configured LLM provider under enterprise no-training terms.
- Engagement and recognition features (leaderboards, leagues, achievements) gated per workspace.
- Internal self-hosted error telemetry (Sentry on TUM infrastructure) and optional product analytics (PostHog) gated by the Contributor's research-participation switch in profile settings.
- No special-category data (Art. 9 GDPR). No Art. 22 automated decision-making.
- Residual elevated risk on the AI-assisted feature surface is covered by the BayLfD innovative-technology criterion and the mitigations documented in `02-dsfa-prescreen.md` §5.

## Annual refresh

Re-review the VVT once per year:

- Has the deployed stack changed? (new processor, new data category, new retention window?)
- Has the platform added a new LLM provider or a new source system? Any of these requires an amended VVT, an amended privacy page, and a new row in the AVV checklist.
- Are the retention figures in `03-vt-dsms.md` still matching the deployed config (server-log rotation, PostgreSQL / Keycloak backup schedules, LLM provider retention windows, Sentry event retention, PostHog event retention where enabled)?
- Has the scope of AI-assisted features grown to the point that the DPIA pre-screen in `02-dsfa-prescreen.md` must be upgraded to a full DPIA under the BayLfD template?

## Emergency — DSB rejection

The DSB may comment in DSMS. Typical follow-ups and responses:

- *"Rechtsgrundlage zu konkretisieren"* → §7 of the VVT cites Art. 6(1)(e) GDPR + Art. 4 Satz 1 BayHIG + Art. 25 Abs. 1 BayDSG for TUM Contributors, and Art. 6(1)(b) GDPR for non-TUM Contributors. Point them there.
- *"Löschkonzept fehlt"* → §13 of the VVT lists retention per category, including the account-deletion flow and the hard 14-day server-log cap.
- *"AVV fehlt für X"* → see [`05-avv-checklist.md`](./05-avv-checklist.md) for the per-processor DPA status.
- *"DSFA erforderlich"* → upgrade [`02-dsfa-prescreen.md`](./02-dsfa-prescreen.md) to the BayLfD DPIA template; the pre-screen already captures the residual-risk structure a full DPIA would elaborate.

Export DSB comments, update the relevant file, and re-submit.

## Contacts

- TUM DPO: [beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de)
- DSMS tool support: [support@datenschutz.tum.de](mailto:support@datenschutz.tum.de)
- TUM DSMS overview: [https://www.datenschutz.tum.de/datenschutz/verarbeitungstaetigkeit/](https://www.datenschutz.tum.de/datenschutz/verarbeitungstaetigkeit/)
- Hephaestus operational contact: [ls1.admin@in.tum.de](mailto:ls1.admin@in.tum.de)
