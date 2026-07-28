# 0028 — Practice reports are criterion-referenced, and reads of them are disclosed

## Status

Accepted

## Context

Hephaestus shipped a leaderboard: XP, leagues, an ordering of people. It is flag-gated off everywhere and
being removed (#1374). What replaces it is a **practice report** — the same underlying observations, read
against the practice's stated standard instead of against colleagues.

The temptation on the way out is to keep the shape and soften the label: a "practice score", a "team ranking"
with kinder colours. That would be the leaderboard with better manners. Two things make it worth writing the
prohibition down rather than relying on taste:

1. **Norm-referenced comparison between teammates is the wrong instrument for growth.** The target moves with
   the cohort, the judgement is relative rather than about the work, and comparing colleagues costs the trust
   collaboration runs on. Criterion-referenced evaluation compares work to an articulated standard with
   evidence as the anchor — which is the only form a person can act on.
2. **A field name is a product decision.** Ship `score` on the wire and a client will render a sorted list,
   however the docs frame it. The invariant has to live where a future contributor trips over it, not in a
   design doc they have not read.

Separately, this read model creates a new kind of exposure. The leaderboard showed everyone the same public
ordering. A practice report shows **one named person's assessed work to another person** — a mentor opening a
developer's report. That is a disclosure, and a system that performs it cannot answer "who has seen my
feedback" from memory.

## Decision

### 1. No practice surface is norm-referenced

No score, rank, percentile, league or total reaches the wire. A developer's standing is one of four
criterion-referenced statuses (`DEVELOPING` / `STRENGTH` / `MIXED` / `NO_ACTIVITY`) and a trend against their
own previous window — never against anyone else's.

The roster does order rows, by `needsAttention` first. That is a triage aid for a mentor's limited time, and
it is kept honest two ways: the flag is a boolean, not a magnitude, and its reasons are rendered in words
("Testing: gaps to work on this window"), so there is no number for a reader to compare across rows.

`NonCompetitiveSurfaceArchTest` fails the build on a DTO field whose name segments read as a score or a rank.
It is a name check, so it is defeatable by someone determined — that is fine. It is aimed at drift, not
malice: the field that gets added in a hurry, three years from now, by someone who never read this file.

### 2. Aggregates are anonymised twice

Workspace health is a distribution per practice area, never per person, and it is withheld when publishing it
would identify individuals. Two rules, both required:

- **Group floor, K = 5.** Fewer than five active developers in an area and there are no counts. Five is the
  threshold GitHub applies to its Copilot metrics API and the lowest Microsoft permits in Viva Insights
  (whose default is ten).
- **Masked distributions.** A bucket holding 1–4 people points at those people. A bucket holding *all* of
  them is worse, not safer: it discloses every member's status at once, and the group floor does not catch it
  because the group is large. So a bucket is also suppressed when its complement falls below K. Viva Insights
  masks "almost all / almost none" for exactly this reason.

Admins and owners see unsuppressed counts. They already see every developer by name on the roster and the
drill-down, so suppressing their aggregate would protect nobody while blinding a mentor on a small team.

### 3. Reads that name a person are recorded

`GET /practices/reports` and `GET /practices/reports/{userId}` write an append-only row to
`data_access_event` before the response returns. Failure to record fails the request: an unrecordable
disclosure is refused rather than served unrecorded.

`GET /practices/reports/me` records nothing. Reading your own data is not a disclosure to anyone, and logging
it would bury the rows that matter in noise.

The trail is a peer of `config_audit_event` (configuration *changes*) and `auth_event` (authentication
forensics), not a reuse: different identity namespace — SCM actor ids, which a person can occupy without ever
having signed in — answering a different question. It carries a 365-day retention window, because a record of
who read whose feedback is itself personal data and storage limitation applies to the audit as much as to
what it audits.

**The subject can read their own disclosures, and nobody else can read anyone's.** The existing GDPR data
export (account settings → "Danger Zone") carries a `dataDisclosures` section: when, in which workspace, on
which surface, and — the part that makes it an answer rather than a gesture — the login of the person who
looked. The CJEU held in C-154/21 that Art. 15(1)(c) entitles a subject to the *identity* of recipients, not
merely their category. It is also the right shape for a mentoring tool: the asymmetry of "they can read your
assessed work and you cannot see that they did" is what makes software feel like surveillance.

There is deliberately no **administrator-facing** read. A trail whose purpose is to make watching visible
must not become another way to watch — "which reports has this admin been opening" is a surveillance
question wearing a compliance hat. A dedicated transparency page for the subject remains #1196's scope; this
is the compliance floor under it, not a substitute for it.

### 4. The report window is a duration, not a schedule

A report covers the last N days (default 28), truncated to day boundaries, with the trend measured against
the equally-long window before it.

The rejected alternative was the one already implemented on the predecessor branch: anchor the window to the
workspace's weekly digest schedule, reading the day-of-week and time-of-day off
`workspace.leaderboard_schedule_day` / `_time`. Three problems, any one sufficient. It inherits the
leaderboard's weekly reset — the competitive rhythm this read model exists to replace. It couples a new
surface to columns #1374 deletes. And it produces a one-to-two-week window, thin enough that an ordinary
contributor opens an empty report and concludes the product has nothing to say.

A duration is the honest primitive: one knob (`hephaestus.practice-review.report-window-days`), no timezone
arithmetic, and widening it is how a low-volume workspace gets a report worth reading. Day truncation is what
keeps it stable — an untruncated rolling window slides with every request, so an item silently vanishes
between two refreshes and the two windows the trend compares are never quite the same length.

## Consequences

- The webapp cannot render a practice leaderboard without deleting an architecture test, which is the point.
- `/practices/observations/reflection` is replaced by `/practices/reports/me`. Pre-1.0, no webapp consumer,
  and one noun for one concept is worth the rename.
- Workspace health can read "suppressed" on a healthy team where everyone is at STRENGTH. That is correct —
  publishing it would tell every member exactly where every colleague stands — but it needs saying in the UI,
  or it reads as a bug.
- The disclosure trail grows one row per served named report and is not deduplicated. Every response that
  discloses a named report *is* a disclosure; a re-opened dialog produces true repeat events. Clients avoid
  gratuitous refetches rather than the writer suppressing real ones.

## References

- [GitHub Copilot metrics API — five-member minimum](https://docs.github.com/en/rest/copilot/copilot-metrics)
- [Viva Insights — minimum group size, masked distributions, differential privacy](https://learn.microsoft.com/en-us/viva/insights/advanced/privacy/privacy)
- [GDPR Art. 15 — right of access, including recipients](https://gdpr-info.eu/art-15-gdpr/)
- ADR 0021 (findings ↔ feedback synthesis seam), ADR 0022 (observation presence and assessment)
