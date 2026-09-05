---
"hephaestus": patch
---

A worker host shares the instance's rate-limit store for its sandbox gateway rather than keeping a
private one, so the gateway's limit behaves the same however many workers run.
