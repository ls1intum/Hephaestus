# ADR 0023: Replace the competitive leaderboard with practice surfaces

**Status:** Accepted
**Date:** 2026-07-10
**Authors:** Felix T.J. Dietrich
**Supersedes (in part):** the leaderboard/leagues surfaces assumed by [ADR 0015](0015-unified-integration-framework.md) (Slack digest fan-out) and referenced in [ADR 0019](0019-workspace-membership-keyed-on-account.md)

## Context

Hephaestus ranked developers on a weekly leaderboard with leagues, ELO-style league points, and XP,
fanned out as a Slack digest. That competitive frame conflicts with the platform's formative-mentoring
goal: feedback research (Kluger & DeNisi 1996) predicts that a rank frame directs attention to the self
and the standing, not the task. Worse, the ranked quantity here is *review activity* — scoring it
incentivizes exactly the anti-patterns the practice catalogue exists to detect: nitpick comments to farm
points and rubber-stamp approvals to farm throughput. A leaderboard of review practices is a Goodhart
target on the pedagogy itself.

## Decision

Delete the competitive stack — leaderboard, leagues, ELO/league points, XP, and the weekly Slack digest
fan-out. Replace it with two non-competitive surfaces, both served by `PracticeReportController` and
neither carrying a score or rank:

1. **My Practices** — the developer's own per-practice reflection cards on the workspace home
   (`GET /practices/reports/me`), server-gated to the caller.
2. **Practice Overview** — a mentor/admin surface (workspace ADMIN/OWNER only): a roster of
   per-developer summaries sorted needs-attention-first then login (`GET /practices/reports`), a
   per-developer drill-down (`GET /practices/reports/{userId}`), and an anonymised cohort rollup
   (`GET /practices/cohort`). The roster and cohort cover **every** practice area at an area-rollup
   grain (one cell/card per area); the drill-down keeps per-practice detail. Each area cell and each
   reflection card carries a **cycle-over-cycle trend** (IMPROVING / WORSENING / STEADY / NEW) — the
   trajectory a mentor and developer act on — so the surface answers "is this getting better?", not
   just "how does it look right now". (An earlier P1 iteration scoped the mentor surfaces to the single
   `constructive-code-review` area; a live fill-test showed that left the mentor blind to risk
   concentrated in other areas, so the generalisation landed in this ADR's scope.)

Guardrails shipped with the surfaces:

- **`cohortVisibility` workspace feature** — `MENTORS_ONLY` (default) or `EVERYONE` — controls whether
  regular members may read the cohort aggregate.
- **K = 5 small-cell suppression** on the cohort rollup, so no member can de-anonymise a colleague from
  a small bucket.
- **Read audit** — every serving of the named roster or a drill-down writes an append-only
  `DataAccessEvent` row (`core.audit`: actor, subject, resource type, timestamp).

The append-only `ActivityEvent` log and the achievements module are **kept**: achievements recognize
personal milestones without ranking members against each other, and the activity log is their substrate.

## Consequences

- No surface anywhere orders developers against each other; the roster's needs-attention-first sort is
  triage, not ranking.
- Mentor visibility into named reports is now an audited disclosure rather than a public scoreboard —
  a strictly smaller and accountable exposure of personal data.
- **Individual vs. aggregate, deliberately split by threat model:** the mentor's roster and drill-down
  are named and individual (not k-anonymised) because coaching a person requires seeing that person's
  work — formative-feedback and scaffolding theory (Hattie & Timperley; Vygotsky's ZPD; deliberate
  practice) all require diagnosing *this* learner, which an aggregate cannot do. K-anonymity applies
  only to the cohort rollup, whose threat model is a *member* re-identifying a colleague from a small
  bucket — a threat that does not exist for a mentor who already knows their mentees by name. The
  read-audit and the developer's in-product notice (that admins can see their standing) are what keep
  the individual surface formative-and-accountable rather than surveillance.
- The Slack digest path from ADR 0015 is gone; the Slack module's surviving jobs are mentor DMs and
  consent-gated channel monitoring (#1341).
- **Residual risk:** k-anonymity does not defend homogeneous cells — if all ≥5 developers in a bucket
  share the same status, membership in the bucket still discloses that status. The `MENTORS_ONLY`
  default mitigates by keeping the aggregate away from members unless an admin opts in.
- `league_points` and related columns were dropped; ADR 0019's SCM-attribution examples now read
  `hidden` + practice/activity attribution (see its amendment note).
- Account-erasure of practice observations follows the account/actor split (ADR 0019) and is tracked
  as a follow-up: practice data is keyed on the SCM actor, so `AccountPurger` does not yet remove it.
