---
"hephaestus": minor
---

Administrators can now see a history of changes to a workspace's AI-settings controls — who changed a
setting, when, and from what to what. It covers the practice-review policy, which model is bound to
practice detection and to the mentor, and the run limits on those bindings. Each entry shows
the field-level before/after, keeps the author — including changes made while impersonating another
user — and never stores credentials such as API keys. A workspace administrator finds it under
Administration → "Audit log" for their own workspace; an instance administrator gets a
cross-workspace view under the instance-admin console. The history is append-only and retained for
twelve months. The accompanying database migration adds one table and applies automatically, with no
action required on upgrade.
