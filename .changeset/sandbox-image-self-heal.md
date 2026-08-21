---
"hephaestus": patch
---

Practice reviews recover on their own when the host reclaims the agent image. The image is only referenced while a review runs, so a host that prunes unused images removes it between reviews — and because it was fetched once at startup, every later review failed to start its container and retried into the same failure. The image is now re-established before each run.
