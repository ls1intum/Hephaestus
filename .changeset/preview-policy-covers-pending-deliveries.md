---
"hephaestus": patch
---

A pull request preview now refuses to start if a cloned feedback delivery is still pending. The startup check covered triggers, bindings and running jobs but not the one row that can still reach a real pull request — a completed review whose delivery had not gone out yet — so a preview could come up believing it was silenced while holding a deliverable result.
