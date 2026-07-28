---
"hephaestus": patch
---

Fixes per-workspace LLM spend being over-counted when an agent job was retried after an infrastructure failure: each retry attempt's token usage is now billed to the usage ledger exactly once, so monthly spend and budget-cap enforcement reflect real cost. Also stops a job that died without a recorded price from blocking its own terminal cleanup, and prevents deleting a model that is still bound to a workspace's practice-detection or mentor purpose.
