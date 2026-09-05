---
"hephaestus": patch
---

Practice reviews and Heph conversations run to completion again. After the recent agent fixes every
review still stopped at its first tool call, because the sandbox denied the review its own session
files; the sandbox now prepares that directory before the review starts.
