---
"hephaestus": minor
---

A practice group with no verdict now says which kind of silence it is instead of one catch-all "No feedback yet": whether nothing has been observed yet, or whether the reviewed work simply offered no opportunity for that practice. A developer can tell a reviewer that ran and found nothing from one that was never configured.

Observation severities read as the action they ask for — "Fix now", "Fix before merge", "Nit", "FYI" — instead of claiming a measured "Major impact"/"Minor impact" that the value does not carry. The severity filter uses the same wording as the observations it selects, so a filter option can never be worded differently from the rows it returns.

The evidence behind an observation reads as a quoted file: its path as a header with the line range beside it, the quoted lines below with their line numbers, and one block per file when an observation spans several. Long lines scroll instead of being clipped, and a observation no longer repeats a link to the pull request its own heading already links to.
