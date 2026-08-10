---
"hephaestus": minor
---

A workspace's per-run timeout under Administration → AI models is now capped at one hour (it already
had a 30-second floor). A single agent run has an upper bound again, and the sweep that closes and
bills runs abandoned by a crashed worker is sized from it — previously an unusually long timeout could
let that sweep close a mentor conversation that was still answering. The form catches a longer value
as you type it, with the reason beside the field, instead of sending it and reporting a rejection
that named neither the number nor the limit.

**Operators:** check any workspace whose timeout was set above one hour. Existing settings are kept as
they are, so such a workspace goes on running to its stored value and cannot save any other change on
that page until the timeout is lowered. See `MIGRATION.md`.
