---
"hephaestus": minor
---

Operator review views withhold observation text and evidence when source access can no longer be verified. The review’s outcome, practice, work and delivery history remain visible. Observation summaries are optional in the operator API when this text is unavailable.

**Operators:** External clients of the operator review API must handle unavailable observation summaries. The bundled interface handles this state. No manual database change is required.
