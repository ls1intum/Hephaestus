---
"hephaestus": patch
---

Fixes agent-job scheduling so a workspace with a large backlog of practice-detection jobs at its concurrency cap can no longer starve other workspaces' ready jobs: the poll queue now enforces fairness per workspace-and-purpose (matching the per-purpose model bindings), instead of the obsolete per-config grouping that had stopped taking effect.
