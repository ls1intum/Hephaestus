---
id: submission-guide
title: DSMS Submission Guide
description: Step-by-step procedure for submitting the Hephaestus VT through the TUM DSMS.
---

# Hephaestus — DSMS Submission Guide

Follow these steps in order. Target: [https://dsms.datenschutz.tum.de/](https://dsms.datenschutz.tum.de/) (log in via Shibboleth on MWN / eduVPN).

## Phase 0 — Prep (15 min)

1. Open `03-vt-dsms.md` alongside this guide.
2. Confirm the deployment-dependent figures match reality:
   - Server / reverse-proxy log rotation and retention on the VM (`hephaestus-prod.aet.cit.tum.de`). The current baseline is a **14-day hard maximum** — verify the live `docker` and host-level log-rotation config. Adjust §13 if different.
   - Database (PostgreSQL) and Keycloak backup schedule and retention — verify against the actual AET backup jobs and update §13 if different.
   - LLM provider retention windows in place for each workspace's configured provider (default: 30-day enterprise abuse-monitoring for OpenAI/Azure/Anthropic unless Zero Data Retention has been negotiated).
   - Which LLM providers are enabled in production (workspace administrators may have disabled some). §11 of the VT lists all three; trim if a provider has been removed.
3. Have at hand: TUM login + edit access on the Hephaestus repo (for privacy-page updates).

## Phase 1 — Ship the privacy page (done, but re-verify)

The live privacy page is at [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy). Open it and confirm:

- Controller identified as **TUM + Prof. Krusche (AET)**, with operational contact `ls1.admin@in.tum.de`.
- DPO: **[beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de)**.
- §3.1 names **gitlab.lrz.de / Leibniz-Rechenzentrum der BAdW** as a separate controller under the § 16 BayHIG public-body cooperation framing, **not** an Art. 28 processor.
- §3.2 documents the shared-responsibility model (workspace admin configures LLM credentials, Slack, leaderboards, practice catalog, auto-trigger).
- §4 lists: identity + authentication, development activity (GitHub + gitlab.lrz.de), account settings + recognition, AI-assisted features, server logs (14-day cap).
- §6 lists every external processor: GitHub, LLM provider per workspace (OpenAI / Azure OpenAI / Anthropic), Slack, TUM SMTP relay.
- §7 covers third-country transfers under DPF + SCC fall-back.
- Legal basis table: **Art. 6(1)(e) GDPR + Art. 4 BayHIG + Art. 25 BayDSG** for TUM Contributors; **Art. 6(1)(b) GDPR** for non-TUM Contributors; **Art. 6(1)(a) GDPR** for the optional notification email.
- Cookies section names only Keycloak session cookies and the theme-preference localStorage key under **§ 25(2) Nr. 2 TDDDG**.
- Complaint authority: **Bayerischer Landesbeauftragter für den Datenschutz (BayLfD)**.

If any of the above is wrong, fix the Markdown source in `webapp/public/legal/profiles/tumaet/privacy.md` first.

## Phase 2 — Create the VT in DSMS (45 min)

1. Log in at [https://dsms.datenschutz.tum.de/](https://dsms.datenschutz.tum.de/).
2. Click **Create new PA**.
3. Copy **Title** and **Description and Purpose** from `03-vt-dsms.md` ("Step 1" block).
4. Select **Category**: `Administration / Teaching / Other`.
5. Click **Save**. DSMS redirects to the follow-up questionnaire.
6. Fill each follow-up field by copy-pasting from `03-vt-dsms.md` ("Step 2" block, §1 – §21). DSMS-label mapping:

   | DSMS field | VT section |
   |---|---|
   | Responsible unit / Fachabteilung | §1 |
   | Joint controllers | §2 |
   | Auftragsverarbeiter | §3 |
   | DPO | §4 |
   | Zwecke der Verarbeitung | §5 |
   | IT-System / Verfahren | §6 |
   | Rechtsgrundlage | §7 |
   | Kategorien Betroffener | §8 |
   | Kategorien personenbezogener Daten | §9 |
   | Besondere Kategorien | §10 |
   | Empfänger | §11 |
   | Drittländer | §12 |
   | Löschfristen | §13 |
   | TOMs | §14 (upload `04-toms.md`) |
   | Informationspflicht | §15 |
   | Automatisierte Entscheidung | §16 |
   | DSFA | §17 (upload `02-dsfa-prescreen.md`) |
   | Personalrat | §18 |
   | IT-Sicherheit | §19 |
   | Datenquelle | §20 |
   | Kontakt Betroffenenrechte | §21 |

7. Upload attachments listed in §22:
   - Privacy statement snapshot (export [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy) as PDF).
   - DPIA pre-screen — this repo: `02-dsfa-prescreen.md`.
   - TOMs — this repo: `04-toms.md`.
   - AVV checklist — this repo: `05-avv-checklist.md`.
8. Set **Tags**: `Webdienst`, `Lehre`, `KI-gestützt`, `pot. verallgemeinerbar`.
9. Review the DSMS-generated PDF preview if offered.
10. Set **Status → Submitted**.

## Phase 3 — After submission (async, 1–3 weeks)

The DSB reviews and may leave comments. Typical follow-ups:

- *"Rechtsgrundlage zu konkretisieren"* — §7 already cites Art. 6(1)(e) GDPR + BayHIG + BayDSG for TUM members and Art. 6(1)(b) for external Contributors. Point reviewer there.
- *"Löschkonzept fehlt"* — §13 plus the 14-day server-log cap, the account-deletion flow, and per-provider retention windows are the deletion concept.
- *"Ist § 25 TDDDG relevant?"* — the privacy page already states that only Keycloak session cookies and the theme-preference localStorage key are used, both under § 25(2) Nr. 2 TDDDG.
- *"DSFA erforderlich"* — upgrade `02-dsfa-prescreen.md` to the BayLfD DPIA template; the pre-screen already captures the residual-risk structure a full DPIA would elaborate.
- *"AVV-Vertrag für LLM-Provider"* — DPAs are maintained at TUM/AET level for the TUM-operated LLM tenancy; for credentials supplied by a workspace administrator's institution, the DPA is maintained at that institution's level (shared-responsibility model, §3.2 of the privacy page).

Status progression: Draft → Submitted → Precheck Done → Ready for DPO approval → Approved.

## Phase 4 — Annual refresh

Check yearly:

- Re-read this package; does anything disagree with deployed reality?
- Has the app gained a new identity provider, a new source system, a new LLM provider, or new analytics? If yes, amend VT + privacy page before re-submitting.
- Have log-retention, backup-retention, or LLM-provider retention windows changed?
- Has the AI-assisted feature surface grown to the point that a full DPIA (not just the pre-screen) is warranted?

## Emergency — DSB rejects

If the DSB rejects the VT:

1. Export their DSMS comments.
2. Update `03-vt-dsms.md` and — if the substance changed — the privacy-page Markdown under `webapp/public/legal/profiles/tumaet/`.
3. Re-submit.

## Contacts

- Tool support: [support@datenschutz.tum.de](mailto:support@datenschutz.tum.de)
- Substantive questions: [beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de)
- TUM DSMS page: [https://www.datenschutz.tum.de/datenschutz/verarbeitungstaetigkeit/](https://www.datenschutz.tum.de/datenschutz/verarbeitungstaetigkeit/)
