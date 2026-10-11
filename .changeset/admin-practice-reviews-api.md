---
"hephaestus": minor
---

The admin API of Practice reviews has new reads for workspace admins:

- The lineage of an observation, a piece of feedback, a review or a piece of work: the review with when it was asked for, picked up and finished, why a completed review recorded what it did, what started it, the moments on the work, its observations, the feedback written from them, where each piece went, and whether the developer answered, disputes it or their work resolved it. A developer's rating and private notes are never part of it.
- How long reviews took in a range, as medians and 90th percentiles of whole reviews and of each stage, with each stage's part, and the reviews that completed after their work was merged.
- The queue as it stands now, and the delivery checks a review ran for itself.
- Feedback counted by where it went.
- What a review spent on the model, per attempt and in total, with each attempt's tokens and pricing state.
- What the provider reported about a pull request, merge request or issue: its author, state, branches, times, size and the state of its checks.
- Every version of a practice definition, with the parts that changed and whether what a review checks changed.

The review list can filter by a review, a piece of work, a developer, a practice, a group and reviews that completed after the merge. Each review says its place among the reviews of its work and how many there are, why it is on hold, and whether it completed after the merge and whether the merge started it. Feedback carries the permalink of each posted comment, who approved or rejected it, and each delivery to the provider with its state, attempts, next attempt and last error. An observation says when a later change superseded it and whether its review ran live, was requested or was backfilled. A review says why it was canceled and gives the trace id of its logs. The list of recorded work can leave out the work a review started on.

The upgrade adds a database index for the reviews of one piece of work.
