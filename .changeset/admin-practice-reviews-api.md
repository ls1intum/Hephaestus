---
"hephaestus": minor
---

Workspace admins can use the admin API to trace a practice review from its trigger through observations and delivery, inspect the current queue, review timing and model usage, and read recorded work and practice versions. Shared filters and counts agree across these reads, including reviews that finished after a merge.

Missing or deleted work is distinguished from a failed read, reviews waiting before a retry are excluded from the claimable queue, and withheld feedback is no longer reported as a posted comment. Private feedback text, ratings and private response notes remain hidden from workspace admins.

**Operators:** Clients that read raw job output must use the dedicated observation and feedback endpoints instead. Administrative job responses no longer return raw output; typed review outcomes, timing and usage remain available.
