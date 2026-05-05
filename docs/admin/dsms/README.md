---
id: dsms
sidebar_position: 3
title: DSMS Submission Package
description: Art. 30 GDPR / Verarbeitungstätigkeit record for the TUM-operated Hephaestus deployment.
---

_Last updated: 2026-05-05._

This directory is the complete record-of-processing (Art. 30 GDPR / "Verzeichnis von Verarbeitungstätigkeiten", VVT) package for the TUM-operated Hephaestus deployment at `https://hephaestus.aet.cit.tum.de`. Submit it through the TUM DSMS at **[https://dsms.datenschutz.tum.de/](https://dsms.datenschutz.tum.de/)** (reachable from MWN / eduVPN with TUM login).

## Contents

| File                                             | Purpose                                                                                                          |
| ------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------- |
| [`README.md`](./README.md)                       | This file                                                                                                        |
| [`01-submission-guide.md`](./01-submission-guide.md)   | Ordered submission procedure                                                                                     |
| [`02-dsfa-prescreen.md`](./02-dsfa-prescreen.md) | DPIA pre-check (Art. 35 GDPR) — records the DPIA-light posture and the conditions that would require a full DPIA |
| [`03-vvt.md`](./03-vvt.md)               | Copy-paste VVT answers for the DSMS form                                                                         |
| [`04-toms.md`](./04-toms.md)                     | Technical and Organizational Measures (Art. 32 GDPR)                                                             |
| [`05-avv-checklist.md`](./05-avv-checklist.md)   | Art. 28 processor checklist — every external and internal recipient and its AVV status                           |

The live imprint and privacy pages are served at:

- [https://hephaestus.aet.cit.tum.de/imprint](https://hephaestus.aet.cit.tum.de/imprint)
- [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy)

Markdown source lives under [`webapp/public/legal/profiles/tumaet/`](https://github.com/ls1intum/Hephaestus/tree/main/webapp/public/legal/profiles/tumaet).

## Summary of the processing surface

- Federated identities via Keycloak (GitHub OAuth + gitlab.lrz.de OIDC).
- Repository synchronisation from GitHub and gitlab.lrz.de into workspace-scoped datasets.
- AI-assisted guidance and automated practice review calling a workspace-configured LLM provider under enterprise no-training terms.
- Engagement and recognition features (leaderboards, leagues, achievements) gated per workspace.
- No special-category data (Art. 9 GDPR). No Art. 22 automated decision-making.
- Residual elevated risk on the AI-assisted feature surface is covered by the BayLfD innovative-technology criterion and the mitigations documented in `02-dsfa-prescreen.md` §5.

## Maintenance

The VVT is re-reviewed annually and whenever the processing surface changes (new processor, new data category, new retention window, new identity provider, or activation of an integration that is currently disabled). The triggers that require an amendment before go-live are listed in [`05-avv-checklist.md`](./05-avv-checklist.md) and [`02-dsfa-prescreen.md`](./02-dsfa-prescreen.md) §6.

## DSB feedback

If the DSB leaves comments after submission, see Phase 3 of [`01-submission-guide.md`](./01-submission-guide.md).

## Contacts

- TUM DPO: [beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de)
- DSMS tool support: [support@datenschutz.tum.de](mailto:support@datenschutz.tum.de)
- TUM DSMS overview: [https://www.datenschutz.tum.de/datenschutz/verarbeitungstaetigkeit/](https://www.datenschutz.tum.de/datenschutz/verarbeitungstaetigkeit/)
- Hephaestus operational contact: [ls1.admin@in.tum.de](mailto:ls1.admin@in.tum.de)
