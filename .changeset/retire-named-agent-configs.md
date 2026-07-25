---
"hephaestus": minor
---

Retires named agent configurations. A workspace's AI setup is now exactly one model binding per purpose — practice detection and mentor — edited on the **AI models** page, instead of a list of named configs plus separate pointers designating which one each feature used. Existing configurations were migrated to bindings automatically, so detection and the mentor keep running on the model they already used.

**Operators:** the `/agent-configs` endpoints and `PUT /ai-settings/practice-config` / `PUT /ai-settings/mentor-config` are gone; use `GET|PUT|DELETE /workspaces/{workspaceSlug}/agent-bindings/{purpose}`. `GET /ai-settings` no longer returns `practiceConfigId`/`mentorConfigId`, and agent jobs report the model they ran on rather than a config name. Any script calling the removed endpoints must be updated before upgrading.
