---
id: submission-guide
title: DSMS Submission Guide
description: Step-by-step procedure for submitting the Hephaestus VVT through the TUM DSMS.
---

_Last updated: 2026-05-05._

Follow these steps in order. Target: [https://dsms.datenschutz.tum.de/](https://dsms.datenschutz.tum.de/) (log in via Shibboleth on MWN / eduVPN).

## Phase 0 — Prep (15 min)

1. Open `03-vvt.md` alongside this guide.
2. Confirm the deployed configuration still matches the figures pinned in `03-vvt.md` §13 (access-log retention, off-host-backup state, LLM-provider list, the disabled state of the Sentry / PostHog / Keycloak-SMTP integrations). Any drift triggers an amendment to the corresponding section before submission. The full amendment-trigger list is in [`05-avv-checklist.md`](./05-avv-checklist.md), "Follow-up if the VVT surface changes".
3. Have at hand: TUM login and edit access on the Hephaestus repo (for any privacy-page updates).

## Phase 1 — Ship the privacy page

The live privacy page is at [https://hephaestus.aet.cit.tum.de/privacy](https://hephaestus.aet.cit.tum.de/privacy). Open it and confirm:

- Controller identified as **TUM + Prof. Krusche (AET)**, with operational contact `ls1.admin@in.tum.de`.
- DPO: **[beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de)**.
- §3.1 names **gitlab.lrz.de / Leibniz-Rechenzentrum der BAdW** as a separate controller under the public-body cooperation framing, **not** an Art. 28 processor.
- §3.2 documents the shared-responsibility model (workspace admin configures LLM credentials, Slack, leaderboards, practice catalog, auto-trigger) with the Art. 26(2) Satz 1 duty allocation.
- §4 lists: identity + authentication, development activity (GitHub + gitlab.lrz.de), account settings + recognition + the "AI review comments" Art. 21 objection switch, AI-assisted features, and server logs (minimal application-server web access log, retained 14 days, then deleted automatically). It does **not** claim active Sentry or PostHog processing — both integrations are disabled in production.
- §6 lists every recipient: GitHub, the LLM provider per workspace (OpenAI or Azure OpenAI), and Slack.
- §7 covers third-country transfers under DPF + SCCs Module 2 fall-back.
- Legal basis table: **Art. 6(1)(e) GDPR + Art. 2 BayHIG + Art. 4 Abs. 1 BayDSG** for TUM Contributors; **Art. 6(1)(b) GDPR** for non-TUM Contributors.
- Cookies section names only Keycloak session cookies and the theme-preference localStorage key under **§ 25 Abs. 2 Nr. 2 TDDDG**.
- Complaint authority: **Der Bayerische Landesbeauftragte für den Datenschutz (BayLfD)**.

If any of the above is wrong, fix the Markdown source in `webapp/public/legal/profiles/tumaet/privacy.md` first.

## Phase 2 — Create the VVT in DSMS (45 min)

1. Log in at [https://dsms.datenschutz.tum.de/](https://dsms.datenschutz.tum.de/).
2. Click **Create new PA**.
3. Copy **Title** and **Description and Purpose** from `03-vvt.md` ("Step 1" block).
4. Select **Category**: `Administration / Teaching / Other`.
5. Click **Save**. DSMS redirects to the follow-up questionnaire.
6. Fill each follow-up field by copy-pasting from `03-vvt.md` ("Step 2" block, §1 – §23). DSMS-label mapping:

   | DSMS field                         | VVT section                                               |
   | ---------------------------------- | --------------------------------------------------------- |
   | Responsible unit / Fachabteilung   | §1                                                        |
   | Joint controllers                  | §2                                                        |
   | Auftragsverarbeiter                | §3                                                        |
   | DPO                                | §4                                                        |
   | Zwecke der Verarbeitung            | §5                                                        |
   | IT-System / Verfahren              | §6                                                        |
   | Rechtsgrundlage                    | §7                                                        |
   | Kategorien Betroffener             | §8                                                        |
   | Kategorien personenbezogener Daten | §9                                                        |
   | Besondere Kategorien               | §10                                                       |
   | Empfänger                          | §11                                                       |
   | Drittländer                        | §12                                                       |
   | Löschfristen                       | §13                                                       |
   | TOMs                               | §14 (paste from `04-toms.md`)                             |
   | Informationspflicht                | §15                                                       |
   | Automatisierte Entscheidung        | §16                                                       |
   | DSFA                               | §17 (paste DPIA-light conclusion; full record in `02-dsfa-prescreen.md`) |
   | Personalrat                        | §18                                                       |
   | IT-Sicherheit                      | §19                                                       |
   | Datenquelle                        | §20                                                       |
   | Kontakt Betroffenenrechte          | §21                                                       |
   | Anlagen                            | §22 (no uploads — see §22 of the VVT)                     |
   | Status                             | §23 (set to Submitted)                                    |

7. Set **Tags**: `Webdienst`, `Lehre`, `KI-gestützt`, `pot. verallgemeinerbar`.
8. Review the DSMS-generated PDF preview if offered.
9. Set **Status → Submitted**.

## Phase 3 — After submission (async, 1–3 weeks)

The DSB reviews and may leave comments. Typical follow-ups:

- _"Rechtsgrundlage zu konkretisieren"_ — §7 already cites Art. 6(1)(e) GDPR + Art. 2 BayHIG + Art. 4 Abs. 1 BayDSG for TUM Contributors and Art. 6(1)(b) for external Contributors. Point the reviewer there.
- _"Löschkonzept fehlt"_ — §13 plus the 14-day application-server access-log retention, the account-deletion flow, and the per-provider retention windows together form the deletion concept. Off-host backups are not currently in place, so no separate backup-deletion flow is required; if backups are introduced later, the application-level deletion log must be re-applied on restore.
- _"Ist § 25 TDDDG relevant?"_ — the privacy page already states that only Keycloak session cookies and the theme-preference localStorage key are used, both under § 25 Abs. 2 Nr. 2 TDDDG.
- _"DSFA erforderlich"_ — upgrade `02-dsfa-prescreen.md` to the BayLfD DPIA template; the pre-screen already captures the residual-risk structure a full DPIA would elaborate and names the conditions under which a full DPIA must be opened (see §6 of the pre-screen).
- _"AVV-Vertrag für LLM-Provider"_ — DPAs are maintained at TUM/AET level for the TUM-operated LLM tenancy; for credentials supplied by a workspace administrator's institution, the DPA is maintained at that institution's level (shared-responsibility model, §3.2 of the privacy page).

Status progression: Draft → Submitted → Precheck Done → Ready for DPO approval → Approved.

## Phase 4 — Annual refresh

Check yearly:

- Re-read this package; does anything disagree with deployed reality?
- Has the app gained a new identity provider, a new source system, a new LLM provider, or activated the built-in Sentry/PostHog integrations? If yes, amend VVT + privacy page + AVV checklist before re-submitting.
- Have application-server log-retention settings, backup arrangements, or LLM-provider retention windows changed?
- Has the AI-assisted feature surface grown to the point that a full DPIA (not just the pre-screen) is warranted?
- Re-verify DPF certification status for each U.S. recipient against the U.S. Department of Commerce list.

## Emergency — DSB rejects

If the DSB rejects the VVT:

1. Export their DSMS comments.
2. Update `03-vvt.md` and — if the substance changed — the privacy-page Markdown under `webapp/public/legal/profiles/tumaet/`.
3. Re-submit.

## Contacts

- Tool support: [support@datenschutz.tum.de](mailto:support@datenschutz.tum.de)
- Substantive questions: [beauftragter@datenschutz.tum.de](mailto:beauftragter@datenschutz.tum.de)
- TUM DSMS page: [https://www.datenschutz.tum.de/datenschutz/verarbeitungstaetigkeit/](https://www.datenschutz.tum.de/datenschutz/verarbeitungstaetigkeit/)
