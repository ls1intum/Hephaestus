---
"hephaestus": minor
---

Practice editors now frame review as AI-supported practice mentoring. Authors define one observable habit, then choose AI-supported mentoring, human review, or guidance only. Recommended review timing and evidence handle the common path, while technical settings, source quality, and limitations remain available when needed. The choice only controls what Hephaestus may review and does not limit what a developer, peer, or human mentor can observe. Missing, incomplete, or outdated required evidence makes Hephaestus skip the practice instead of guessing.

**Operators:** practice review works on a default install — the pull requests and issues from a connected workspace need no extra configuration. Sources holding private conversations, such as Slack threads, are read only after you grant them in `HEPHAESTUS_EVIDENCE_SENSITIVE_SOURCE_USES` as `source:purpose` pairs; wildcards are rejected. Use `HEPHAESTUS_EVIDENCE_WITHHELD_SOURCE_KINDS` to withhold a source you do not want read at all. If Outline is enabled, set the same `HEPHAESTUS_INTEGRATION_OUTLINE_ALLOWED_ORIGINS` value on server, worker, and webhook roles, then restart all three. An empty list blocks Outline connections, sync, webhook collection, evidence projection, and identity linking.

**API clients:** replace the old practice-detection purpose and ambiguous review fields with the names in `MIGRATION.md`; the removed names have no aliases.
