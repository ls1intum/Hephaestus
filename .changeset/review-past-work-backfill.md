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

While it runs you can watch it and stop it. If the monthly AI budget runs out part-way, the campaign
**pauses and resumes** where it stopped — it never quietly skips the work it could not afford. When
it finishes it says plainly whether the baseline is whole: the items it could not review are counted
and reported separately from the ones it deliberately walked past, so a campaign that hit errors
cannot announce itself complete over a baseline with gaps in it.

The observations reach the developers they are about. They flow through the same reads as live feedback —
the reflective read model, the mentor's history of what it can refer to, and the earlier observations
a later review is given — each carrying what occasioned it, so a surface can say an item came from a
review of past work rather than passing it off as something that just happened. The admin observations
list can filter on the same thing: live, requested by hand, or from a campaign.

Two things a campaign deliberately does *not* do:

- **It says nothing on the work itself.** Commenting on pull requests that were merged months ago
  would notify everyone still subscribed to them about work nobody can act on. Backfilled observations
  are measured and recorded, and delivered nowhere.
- **It is kept out of your live trends.** Older work has been polished since it was written, so
  mixing the two would show a dramatic improvement on the day you adopted Hephaestus that nobody
  actually made.

Each artifact is measured once, in the state captured when the campaign runs. Nothing here can reconstruct how a pull request
looked while it was being worked on — no draft history, no edit history, no review-thread timing is
retained — so a backfilled measurement describes the work as it is now, not as it was.
