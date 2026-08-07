---
"hephaestus": minor
---

Adopting Hephaestus no longer means starting from zero. Workspace admins get a new **Review past
work** page under Practices that measures work which already existed — "review the pull requests of
the last 30 days" — so a workspace has a baseline from day one instead of waiting weeks for one to
accumulate.

It is deliberately a two-step decision, because it can spend real money. Choosing a range only
produces an estimate: how many pull requests or issues are in scope, and roughly what reviewing them
will cost, based on what this workspace's own reviews have actually cost. Nothing is submitted until
you confirm that estimate, and the confirmation is recorded on the audit log against the admin who
gave it.

While it runs you can watch it and stop it. If the monthly AI budget runs out part-way, the backfill
**pauses and resumes** where it stopped — it never quietly skips the work it could not afford, so
you never end up with a baseline where "not reviewed" and "reviewed, nothing found" look the same.

Two things a backfill deliberately does *not* do:

- **It says nothing on the work itself.** Commenting on pull requests that were merged months ago
  would notify everyone still subscribed to them about work nobody can act on. Backfilled findings
  are measured and recorded, and delivered nowhere.
- **It is kept out of your live trends.** Older work has been polished since it was written, so
  mixing the two would show a dramatic improvement on the day you adopted Hephaestus that nobody
  actually made.

Each artifact is measured once, as it stands today. Nothing here can reconstruct how a pull request
looked while it was being worked on — no draft history, no edit history, no review-thread timing is
retained — so a backfilled measurement describes the work as it is now, not as it was.
