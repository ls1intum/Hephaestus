---
"hephaestus": patch
---

A single AI run can no longer spend past a workspace's monthly cap. Previously the cap was only re-checked against spend that had already been recorded, so one long run could make many provider calls before any of them counted — a workspace with a $1 cap could reach $100 in one run. Each run is now refused as soon as its own calls have used up the remaining budget.

Per-run token counts are also attributed correctly when a run is retried: a slow response arriving after its run was requeued is no longer billed to the retry.

The runs table and run details no longer show a per-run Cost. That number was recorded before AI spend had a ledger, and it was stored at a precision that could not represent cents exactly. Spend now lives on the AI usage page for the workspace and the instance, where it is broken down by month, job type and who pays.
