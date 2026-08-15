---
"hephaestus": patch
---

Practice-review settings now reach the container that acts on them. Whether a review posts a progress note, whether it reacts to the comment that asked for it, and whether it may deliver on already-merged work are all decided while a review runs — in the worker, which was never given those values, so setting them changed nothing. The same is true of the guardrails that bound a review of past work and the timings that decide when an unsettled review opportunity is retried or given up on.

**Operators:** no action. Every setting keeps the value it effectively had, since the worker was falling back to the built-in default. If you had set one of these expecting it to take effect, it will now do so — check `docker/.env.example` for the full list and the defaults.
