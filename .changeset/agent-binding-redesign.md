---
"hephaestus": minor
---

Setting up AI for a workspace is now one page. Under Administration → "AI setup", a workspace
administrator picks the model that runs practice detection and the model that runs the mentor
directly — "Detection runs on ▢", "Mentor runs on ▢" — each with an active toggle, a readiness
indicator, and optional advanced limits (timeout, concurrent runs, internet access). This replaces
the previous flow of creating named configurations and wiring them up on separate pages.

Practice detection now runs exactly the model you assign to it. Previously, a workspace with no
explicit assignment fanned out to every enabled configuration, submitting one review per
configuration for the same event — multiplying both cost and duplicate feedback. A workspace with no
detection model assigned now runs no reviews until one is assigned.

Budget enforcement is more accurate and more honest:

- When a workspace crosses its monthly cap, detection work that was already queued is now **held and
  resumes automatically** once the cap is raised or the month rolls over, instead of being dropped.
- Once a workspace is over its cap, the in-app AI proxy refuses new calls, so a run already in
  progress can no longer keep spending unbounded.
- A run that crashes or times out mid-way now records the calls it actually made, instead of
  reporting zero — so the cap can see spend that used to leak.

**Operators:** two changes may need action. (1) The instance-wide "usage without a known price"
Warn/Block setting has been removed; a workspace that has a cap set and unverifiable spend is now
always paused (a cap you cannot verify is not a cap), while an uncapped workspace is never paused —
no configuration is needed. (2) Practice detection no longer runs on every configuration by default:
if any workspace relied on that implicit behaviour, assign it a detection model under Administration
→ AI setup for its reviews to resume.
