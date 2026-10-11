---
"hephaestus": patch
---

Practice reviews in workspace administration count and filter reviews the same way everywhere.

- **Until asked** on the review time no longer counts a review that a developer or an admin asked for as a wait of zero seconds, so its count and median show only the reviews a moment on the work started.
- A row on the **Reviews** tab no longer counts an observation marked incorrect in its observation counts, as its practices already did not.
- The **Waiting** status filter on **Reviews** uses the same clock as the queue's waiting count, so the list a queue tile opens holds the reviews the tile counts.
- The list of recorded work answers a filter by one item with an empty kind of work with an error, instead of the items of every kind with that id.
- Review timing and the start of a review read only the signals of the reviews they ask about, so they stay fast as a workspace records more activity.
