---
"hephaestus": patch
---

Fixes the background worker crash-looping on startup when the Slack integration is
enabled. A Slack sync runner was loading in the worker role without the scheduler
it depends on, which only runs on the server role, so the worker failed to boot.
The runner is now gated to the server role, matching the other integration sync
runners.
