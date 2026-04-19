---
id: submission-guide
title: DSMS Submission Guide
description: Step-by-step procedure for submitting the Hephaestus VVT through the TUM DSMS.
---

*Last updated: 2026-04-19.*

Follow these steps in order. Target: [https://dsms.datenschutz.tum.de/](https://dsms.datenschutz.tum.de/) (log in via Shibboleth on MWN / eduVPN).

## Phase 0 — Prep (15 min)

1. Open `03-vt-dsms.md` alongside this guide.
2. Re-confirm the deployment-dependent figures against production before submission. Each of these is already pinned in `03-vt-dsms.md` §13 — the phase-0 check is that the deployment has not drifted:
   - Server / reverse-proxy log rotation at the 14-day hard maximum (`logrotate` + Docker `json-file` log driver on `hephaestus-prod.aet.cit.tum.de`).
   - PostgreSQL and Keycloak backup jobs at 30-day rolling retention on the AET backup host.
   - LLM providers enabled in production and, for each, whether Zero Data Retention is in effect.
   - `SENTRY_DSN` is still empty and `POSTHOG_ENABLED=false` in production. If either has been activated, the VVT, privacy statement, and AVV checklist must be amended first (see `05-avv-checklist.md`, "Follow-up if the VVT surface changes").
3. Have at hand: TUM login + edit access on the Hephaestus repo (for privacy-page updates).

## Phase 1 — Ship the privacy page

The live privacy page is at [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy). Open it and confirm:

- Controller identified as **TUM + Prof. Krusche (AET)**, with operational contact `ls1.admin@in.tum.de`.
- DPO: **[beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de)**.
- §3.1 names **gitlab.lrz.de / Leibniz-Rechenzentrum der BAdW** as a separate controller under the Art. 16 Abs. 1 Satz 2 BayHIG public-body cooperation framing, **not** an Art. 28 processor.
- §3.2 documents the shared-responsibility model (workspace admin configures LLM credentials, Slack, leaderboards, practice catalog, auto-trigger) with the Art. 26(2) Satz 1 duty allocation.
- §4 lists: identity + authentication, development activity (GitHub + gitlab.lrz.de), account settings + recognition + the "AI review comments" Art. 21 objection switch, AI-assisted features, and server logs (14-day cap). It does **not** claim active Sentry or PostHog processing — both integrations are disabled in production.
- §6 lists every recipient: GitHub, the LLM provider per workspace (OpenAI or Azure OpenAI), and Slack.
- §7 covers third-country transfers under DPF + SCCs Module 2 fall-back.
- Legal basis table: **Art. 6(1)(e) GDPR + Art. 4 Satz 1 BayHIG + Art. 25 Abs. 1 BayDSG** for TUM Contributors; **Art. 6(1)(b) GDPR** for non-TUM Contributors.
- Cookies section names only Keycloak session cookies and the theme-preference localStorage key under **§ 25 Abs. 2 Nr. 2 TDDDG**.
- Complaint authority: **Der Bayerische Landesbeauftragte für den Datenschutz (BayLfD)**.

If any of the above is wrong, fix the Markdown source in `webapp/public/legal/profiles/tumaet/privacy.md` first.

## Phase 2 — Create the VVT in DSMS (45 min)

1. Log in at [https://dsms.datenschutz.tum.de/](https://dsms.datenschutz.tum.de/).
2. Click **Create new PA**.
3. Copy **Title** and **Description and Purpose** from `03-vt-dsms.md` ("Step 1" block).
4. Select **Category**: `Administration / Teaching / Other`.
5. Click **Save**. DSMS redirects to the follow-up questionnaire.
6. Fill each follow-up field by copy-pasting from `03-vt-dsms.md` ("Step 2" block, §1 – §23). DSMS-label mapping:

   | DSMS field | VVT section |
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
   | Anlagen | §22 (upload privacy PDF, pre-screen, TOMs, AVV checklist) |
   | Status | §23 (set to Submitted) |

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

- *"Rechtsgrundlage zu konkretisieren"* — §7 already cites Art. 6(1)(e) GDPR + Art. 4 Satz 1 BayHIG + Art. 25 Abs. 1 BayDSG for TUM Contributors and Art. 6(1)(b) for external Contributors. Point the reviewer there.
- *"Löschkonzept fehlt"* — §13 plus the 14-day server-log cap (logrotate + Docker log driver), the account-deletion flow, and per-provider retention windows are the deletion concept.
- *"Ist § 25 TDDDG relevant?"* — the privacy page already states that only Keycloak session cookies and the theme-preference localStorage key are used, both under § 25 Abs. 2 Nr. 2 TDDDG.
- *"DSFA erforderlich"* — upgrade `02-dsfa-prescreen.md` to the BayLfD DPIA template; the pre-screen already captures the residual-risk structure a full DPIA would elaborate and names the conditions under which a full DPIA must be opened (see §6 of the pre-screen).
- *"AVV-Vertrag für LLM-Provider"* — DPAs are maintained at TUM/AET level for the TUM-operated LLM tenancy; for credentials supplied by a workspace administrator's institution, the DPA is maintained at that institution's level (shared-responsibility model, §3.2 of the privacy page).

Status progression: Draft → Submitted → Precheck Done → Ready for DPO approval → Approved.

## Phase 4 — Annual refresh

Check yearly:

- Re-read this package; does anything disagree with deployed reality?
- Has the app gained a new identity provider, a new source system, a new LLM provider, or activated the built-in Sentry/PostHog integrations? If yes, amend VVT + privacy page + AVV checklist before re-submitting.
- Have log-retention, backup-retention, or LLM-provider retention windows changed?
- Has the AI-assisted feature surface grown to the point that a full DPIA (not just the pre-screen) is warranted?
- Re-verify DPF certification status for each U.S. recipient against the U.S. Department of Commerce list.

## Emergency — DSB rejects

If the DSB rejects the VVT:

1. Export their DSMS comments.
2. Update `03-vt-dsms.md` and — if the substance changed — the privacy-page Markdown under `webapp/public/legal/profiles/tumaet/`.
3. Re-submit.

## Contacts

- Tool support: [support@datenschutz.tum.de](mailto:support@datenschutz.tum.de)
- Substantive questions: [beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de)
- TUM DSMS page: [https://www.datenschutz.tum.de/datenschutz/verarbeitungstaetigkeit/](https://www.datenschutz.tum.de/datenschutz/verarbeitungstaetigkeit/)
