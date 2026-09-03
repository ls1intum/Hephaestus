---
"hephaestus": minor
---

Operators can now alert on privacy-job outcomes: account erasure, export generation, export expiry and LLM usage retention each publish success, failure and affected-row counters. A retention pass that runs out of its time budget with rows still expired reports `incomplete` rather than success, so a sweep that never catches up is visible.

LLM usage accounting is no longer kept indefinitely — rows become eligible for deletion 400 days after they were recorded by default, and a daily sweep removes them in batches. The public privacy statement and the record of processing document the window.

**Operators:** the first sweep after upgrade begins deleting usage rows older than the window, and further sweeps continue until the backlog is gone. If your accounting obligations require longer, set `HEPHAESTUS_LLM_USAGE_RETENTION` before upgrading.
