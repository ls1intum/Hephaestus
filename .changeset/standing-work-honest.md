---
"hephaestus": minor
---

A standing in the Practice profile no longer shows "from 4 pieces of work" beside its badge.

The sentence behind the badge now says what the standing rests on. It names the newest reviewed work the standing is read from. It also says how much of your work was reviewed for the practice or the group in the last 90 days. An example: "Read from your last 4 reviewed pull requests. In the last 90 days, 11 pull requests were reviewed for this practice." A group counts each piece of work once.

A tap now opens the sentence of a standing and of a trend. A touch screen reaches it as a pointer and a keyboard do.

The standing and practice group responses carry these counts in `trendSupport`: `trendSupport.standingWork`, `trendSupport.reviewedWork` and `trendSupport.reviewedWorkKind`. This is not the top-level `reviewedWork` field that the Practice profile overview response dropped.
