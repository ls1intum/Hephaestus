---
"hephaestus": patch
---

OAuth return targets and intent cookies now fail closed when excessive encoding or sub-second timestamp boundaries would otherwise bypass their validation limits.
