# ADR 0023: Retire the competitive leaderboard in favor of practice surfaces

**Status:** Accepted
**Date:** 2026-07-12
**Authors:** Felix T.J. Dietrich
**Amends (in part):** the leaderboard/leagues surfaces described by [ADR 0015](0015-unified-integration-framework.md) (Slack digest fan-out) and referenced in [ADR 0019](0019-workspace-membership-keyed-on-account.md)

## Context

Hephaestus ranks developers on a weekly leaderboard with leagues, ELO-style league points, and XP,
fanned out as a Slack digest. That competitive frame conflicts with the platform's formative-mentoring
goal: feedback research (Kluger & DeNisi 1996) predicts that a rank frame directs attention to the self
and the standing, not the task. Worse, the ranked quantity here is *review activity* — scoring it
incentivizes exactly the anti-patterns the practice catalogue exists to detect: nitpick comments to farm
points and rubber-stamp approvals to farm throughput. A leaderboard of review practices is a Goodhart
target on the pedagogy itself.

At the same time, existing workspaces run the leaderboard today, and deleting a live feature in the same
change that introduces its replacement couples two risks (a data-destructive migration and an unproven
new surface) that are safer apart.

## Decision

Retire the competitive stack **in phases**, behind the per-workspace feature flags that already exist:

1. **Now — flag-gated retirement (this decision).** The leaderboard and leagues stay in the codebase
   and the schema, but their per-workspace flags (`leaderboardEnabled`, `leaguesEnabled`) become the
   enforcement point: flag-off workspaces get `404` from the leaderboard/league endpoints and are
   skipped by the weekly job (Slack digest + league-points recompute). New workspaces default to both
   flags off; existing workspaces keep their stored values, so upgrading changes nothing for them. No
   column, table, or code is deleted, and no digest is removed — old application images keep working
   against the migrated schema.
2. **Now — the replacement read model ships dark.** `PracticeReportController` serves two
   non-competitive surfaces, neither carrying a score or rank:
   - **My Practices** — the developer's own per-practice reflection cards
     (`GET /practices/reports/me`), server-gated to the caller.
   - **Practice Overview** — a mentor/admin surface (workspace ADMIN/OWNER only): a roster of
     per-developer summaries sorted needs-attention-first then login (`GET /practices/reports`), a
     per-developer drill-down (`GET /practices/reports/{userId}`), and an anonymised workspace health
     rollup (`GET /practices/health`). The roster and health rollup cover **every** practice area at an
     area-rollup grain (one cell/card per area); the drill-down keeps per-practice detail. Each area
     cell and each reflection card carries a **cycle-over-cycle trend** (IMPROVING / WORSENING /
     STEADY / NEW) — the trajectory a mentor and developer act on — so the surface answers "is this
     getting better?", not just "how does it look right now".

   Dark means: endpoints, schema, and generated client exist; no navigation entry and no user-facing
   UI ships yet. The user-facing surfaces follow after a design pass.
3. **Later — deletion is a separate decision gate.** Dropping the leaderboard code, columns
   (`league_points`, `activity_event.xp`, the feature flags), and docs happens in a dedicated
   retirement change once the practice surfaces have replaced the leaderboard in use. That change gets
   its own review; nothing in this ADR authorizes it implicitly.

Guardrails shipped with the read model:

- **`healthVisibility` workspace setting** — `MENTORS_ONLY` (default) or `EVERYONE` — controls whether
  regular members may read the workspace health aggregate.
- **K = 5 small-cell suppression** on the workspace health rollup for member reads (including compound
  small buckets), so no member can de-anonymise a colleague from a small bucket. Admin/owner reads are
  not suppressed: those roles already see every developer by name on the roster, so suppressing their
  aggregate would protect nobody while blinding the mentor on small teams.
- **Read audit** — every serving of the named roster or a drill-down writes an append-only
  `DataAccessEvent` row (`core.audit`: actor, subject, resource type, timestamp), enforced append-only
  at the storage layer by a trigger.

The append-only `ActivityEvent` log and the achievements module are **kept**: achievements recognize
personal milestones without ranking members against each other, and the activity log is their substrate.

## Consequences

- The new surfaces never order developers against each other; the roster's needs-attention-first sort is
  triage, not ranking. The `NonCompetitiveSurfaceArchTest` pins this on the wire layer: no practice DTO
  field may be named like a score, rank, XP, ELO, or league.
- Mentor visibility into named reports is an audited disclosure rather than a public scoreboard —
  a strictly smaller and accountable exposure of personal data.
- **Individual vs. aggregate, deliberately split by threat model:** the mentor's roster and drill-down
  are named and individual (not k-anonymised) because coaching a person requires seeing that person's
  work — formative-feedback and scaffolding theory (Hattie & Timperley; Vygotsky's ZPD; deliberate
  practice) all require diagnosing *this* learner, which an aggregate cannot do. K-anonymity applies
  only to the workspace health rollup, whose threat model is a *member* re-identifying a colleague from
  a small bucket — a threat that does not exist for a mentor who already knows their mentees by name.
  The read-audit is what keeps the individual surface formative-and-accountable rather than
  surveillance.
- **Residual risk:** k-anonymity does not defend homogeneous cells — if all ≥5 developers in a bucket
  share the same status, membership in the bucket still discloses that status. The `MENTORS_ONLY`
  default mitigates by keeping the aggregate away from members unless an admin opts in.
- During the transition, both processings coexist and both are documented in the DSMS record of
  processing: the flag-gated leaderboard/leagues for workspaces that still have them on, and the
  practice reports with their disclosure audit.
- ADR 0015's Slack weekly-leaderboard digest still exists but only fires for flag-on workspaces (see
  its amendment note); ADR 0019's `league_points` examples remain schema-accurate until the deletion
  phase.

## Revisit trigger

The deletion phase (3) opens once the practice surfaces are live (not dark) and the operator confirms no
production workspace still relies on the leaderboard flag — at which point a dedicated retirement ADR/PR
removes the code, columns, and this transition machinery.
