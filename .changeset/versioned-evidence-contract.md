---
"hephaestus": minor
---

Practice editors now explain the review lifecycle and let authors choose required evidence, optional context, minimum quality, and known limitations. Authors separately configure how Hephaestus may review a practice and whether the selected evidence is sufficient; this does not limit what a developer, peer, or human mentor may review. Practices that Hephaestus cannot review are not used in new reviews and collect no review evidence. Missing, incomplete, or outdated required evidence makes Hephaestus skip the practice instead of guessing.

**Operators:** before enabling practice review, add each controller-approved `source:purpose` grant to `HEPHAESTUS_EVIDENCE_AUTHORIZED_SOURCE_USES`; the default is empty and wildcards are rejected. If Outline is enabled, set the same `HEPHAESTUS_INTEGRATION_OUTLINE_ALLOWED_ORIGINS` value on server, worker, and webhook roles, then restart all three. An empty list blocks Outline connections, sync, webhook collection, evidence projection, and identity linking.

**API clients:** replace the old practice-detection purpose and ambiguous review fields with the names in `MIGRATION.md`; the removed names have no aliases.
