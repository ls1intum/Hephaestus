---
"hephaestus": minor
---

The hourly allowance on hand-requested reviews is now yours to set. It is the only limit keyed on a
person rather than on a piece of work, so it is the one that catches somebody asking for a review on
twenty colleagues' merge requests — the per-work cooldown does not, because those are twenty different
pieces of work. Two smaller review behaviours become settable at the same time: the run-to-run progress
footer with its re-review reply, and dropping points an author has already disputed or marked not
applicable.

Defaults are unchanged, so an upgrade behaves exactly as before.

**Operators:** all optional — `PRACTICE_REVIEW_MAX_REQUESTS_PER_REQUESTER_PER_HOUR` (default `5`, `0`
removes the limit), `PRACTICE_REVIEW_PROGRESS_FOOTER` (default `false`),
`PRACTICE_REVIEW_REACTION_SUPPRESSION` (default `false`).
