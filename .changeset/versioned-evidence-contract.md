---
"hephaestus": minor
---

Practice authors can now declare the exact evidence sources and quality required before Hephaestus evaluates a practice, so unavailable or incomplete inputs produce a conservative refusal instead of an unsupported judgment.

**Operators:** before enabling practice review, set `HEPHAESTUS_EVIDENCE_AUTHORIZED_SOURCE_KINDS` to the comma-separated source kinds approved by your deployment's data controller. The default is empty and collects no practice-detection evidence.
