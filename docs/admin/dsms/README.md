---
id: dsms
sidebar_position: 3
title: Data-Protection Documentation
description: Art. 30 / Art. 35 / Art. 28 records for the TUM-operated Hephaestus deployment.
---

# Hephaestus — Data-Protection Documentation

This folder is the data-protection package for the TUM-operated Hephaestus deployment at https://hephaestus.aet.cit.tum.de. Files are named after the GDPR articles they answer to, not after the TUM DSMS portal field labels. The portal supplies its own field prompts; submit by pasting from the fenced code blocks in `record-of-processing.md` into the corresponding form fields.

A different operator forking Hephaestus must amend, before opening their deployment to users: the controller block in `record-of-processing.md`, the operational-contact email, the Art. 28 row for any processor they engage that is not on the AET pool, the consent / public-task framing in `record-of-processing.md` "Legal basis", and the live privacy notice and imprint under `webapp/public/legal/profiles/`.

## Files

| File | Purpose |
|---|---|
| [`record-of-processing.md`](./record-of-processing.md) | Art. 30 record. TOMs (Art. 32) folded in under Art. 30(1)(g). Fenced blocks paste-ready into the TUM DSMS form. |
| [`dpia-prescreen.md`](./dpia-prescreen.md) | Art. 35 pre-screen. Documents the DPIA-light posture and the conditions that would require a full DPIA. |
| [`processor-checklist.md`](./processor-checklist.md) | Art. 28 checklist. Per-processor AVV status; LRZ-as-separate-controller analysis. |

The live imprint and privacy pages are at https://hephaestus.aet.cit.tum.de/imprint and https://hephaestus.aet.cit.tum.de/privacy. Markdown source: [`webapp/public/legal/profiles/tumaet/`](https://github.com/ls1intum/Hephaestus/tree/main/webapp/public/legal/profiles/tumaet).

## Maintenance

Re-review annually and on any material change to the processing surface (new processor, new data category, new retention window, new identity provider, activation of an integration that is currently disabled). The triggers that require an amendment before go-live are listed in `processor-checklist.md` and `dpia-prescreen.md` §5–§6.

## Contacts

- TUM DPO: [beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de).
- Hephaestus operational contact: [ls1.admin@in.tum.de](mailto:ls1.admin@in.tum.de).
- TUM DSMS portal: https://dsms.datenschutz.tum.de/ (reachable from MWN / eduVPN with TUM login).
