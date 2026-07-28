---
"hephaestus": minor
---

A workspace's per-run timeout under Administration → AI models is now capped at one hour (it already
had a 30-second floor). A single agent run has an upper bound again, and the sweep that closes and
bills runs abandoned by a crashed worker is sized from it — previously an unusually long timeout could
let that sweep close a mentor conversation that was still answering.

**Operators:** no action is needed, and existing settings are kept as they are. A workspace whose
timeout was previously set above one hour keeps the stored value but its runs now stop at one hour;
lower the value under Administration → AI models if you want the setting to match what runs.
