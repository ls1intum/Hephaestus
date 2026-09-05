---
---

No release note: nothing an operator runs or an instance serves changes. The record of whether a
stored credential could be read moves from a hand-rolled thread pool onto the shared task-executor
configuration, which already owns pool sizing and orderly shutdown for every other background lane,
and four leftovers in the same feature are tidied.
