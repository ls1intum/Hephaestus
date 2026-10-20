---
"hephaestus": patch
---

A model call that returns after its review was cancelled or retried is now counted in the spending of the attempt that made it, at that attempt's price. It no longer drops, and it never changes the review's result or the attempt that runs now. This covers calls whose provider reported usage. It does not restore usage lost before this change. Before you upgrade, let old workers finish their running reviews with a graceful drain; queued reviews need not wait.
