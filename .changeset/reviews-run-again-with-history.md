---
"hephaestus": patch
---

Fixes practice reviews failing immediately instead of running. Every review — whether it came from a
push, a sync, the "Review this now" button or a scheduled sweep — stopped before it read anything,
and the work was left with no feedback at all. Reviews now run and deliver as before, with the
reviewer's record of what earlier reviews found and already said.
