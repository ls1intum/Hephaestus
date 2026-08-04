---
"hephaestus": minor
---

Practice editors now frame review as AI-supported practice mentoring. Authors define one observable habit, then choose AI-supported mentoring, human review, or guidance only. Recommended review timing and evidence handle the common path, while technical settings, source quality, and limitations remain available when needed. The choice only controls what Hephaestus may review and does not limit what a developer, peer, or human mentor can observe. Missing, incomplete, or outdated required evidence makes Hephaestus skip the practice instead of guessing.

**Operators:** before enabling practice review, add each controller-approved `source:purpose` grant to `HEPHAESTUS_EVIDENCE_AUTHORIZED_SOURCE_USES`; the default is empty and wildcards are rejected. If Outline is enabled, set the same `HEPHAESTUS_INTEGRATION_OUTLINE_ALLOWED_ORIGINS` value on server, worker, and webhook roles, then restart all three. An empty list blocks Outline connections, sync, webhook collection, evidence projection, and identity linking.

**API clients:** replace the old practice-detection purpose and ambiguous review fields with the names in `MIGRATION.md`; the removed names have no aliases.
