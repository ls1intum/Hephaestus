---
"hephaestus": patch
---

Speeds up the reflection dashboard and the mentor's review history. Checking whether each observation's evidence may be shown used to cost one database round trip per observation, so a developer with a few months of review history waited on hundreds of them; the check now covers a whole page in a single query.
