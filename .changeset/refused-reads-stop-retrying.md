---
"hephaestus": patch
---

Pages stop retrying access-denied and not-found problem responses. Cached information is removed after that refusal, and a later temporary error cannot restore it. Connection failures and busy-server responses retain their retry budgets.
