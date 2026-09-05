---
---

No release note: the two retention sweeps keep the batch size, the time budget and the log lines they
already had. They now share one implementation of the batching loop instead of two copies of it, and
a retention window configured as zero or negative is refused by the same validation every other
duration setting uses.
