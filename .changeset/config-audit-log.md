---
"hephaestus": patch
---

Changes to a workspace's AI-settings controls are now recorded in an audit trail, so administrators
can see who changed a control, when, and from what to what. This covers the practice-review policy,
the agent configurations bound to practice detection and the mentor, and edits to those agent
configurations. Every entry keeps the author — including actions taken while impersonating another
user — alongside the values before and after the change; credentials such as API keys are never
stored. Workspace administrators can review their own workspace's history, and instance
administrators can review it across all workspaces. The trail is append-only and retained for twelve
months. The accompanying database migration adds one table and applies automatically, with no action
required on upgrade.
