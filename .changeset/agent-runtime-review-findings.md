---
---

No user-facing or operator-facing effect. Review follow-up to the Bun agent runtime: a precompute
validation tool no longer discards the runner's exit code, a failing scripts type-check names its own
fix command instead of a different one, a live test that still built its sandbox from a Node image
now uses the agent image, and three live tests that installed a different SDK version than the image
ships are aligned with it. Comments describing the runtime before it moved to Bun are corrected or
removed.
