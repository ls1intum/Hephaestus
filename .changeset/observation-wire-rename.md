---
"hephaestus": minor
---

Practice-review screens now consistently call a recorded measurement an **observation** and the intervention derived from it **feedback**. Headings, filters, empty states, deletion warnings, and user documentation use the same terms. Old bookmarked web pages under `/admin/practices/reviews/findings` redirect to their observation equivalents.

**API clients:** observation payloads now use `artifactKind`, `summary`, `evidenceRationale`, and `deliveredFeedback` in place of `artifactType`, `title`, `reasoning`, and `guidance`. They also expose the observation's origin and claim currentness. The model-reported `confidence` field is removed because it was not a calibrated measurement. The removed API fields have no aliases; the bundled web app already uses the new contract. See `MIGRATION.md`.
