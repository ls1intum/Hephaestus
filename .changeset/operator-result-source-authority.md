---
"hephaestus": minor
---

Operator review views withhold result text and evidence when source access can no longer be verified. The review’s outcome, practice, work and delivery history remain visible. Result summaries can be null in the operator API when this text is unavailable.

**Operators:** External clients of the operator review API must handle unavailable result summaries. The bundled interface handles this state. No manual database change is required.
