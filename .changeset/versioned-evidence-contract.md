---
"hephaestus": minor
---

Practice authoring is now framed as AI-supported practice mentoring. An author states one observable
habit and then chooses how it is supported: **AI-supported mentoring**, **Human review needed**, or
**Guidance only**. That choice governs only what Hephaestus may review; it never limits what a
developer, a peer or a human mentor can observe.

Review timing and evidence are now stated per occasion, so a practice can ask for different evidence
when work arrives than when it is merged, with a recommended timing and evidence set covering the
common path.

Required evidence that is missing, that could only be captured in part, or that turned out to be
empty makes Hephaestus skip the practice and say which, instead of guessing from what it had.

After practice review and its model are enabled, the shipped pull-request and issue practices need no
additional evidence configuration.

**Operators:** if Outline is enabled, set the same `HEPHAESTUS_INTEGRATION_OUTLINE_ALLOWED_ORIGINS`
value on the server, worker and webhook roles, then restart all three. An empty list blocks Outline
connections, sync, webhook collection, evidence projection and identity linking. See `MIGRATION.md`.

**API clients:** the AI purpose that runs practice reviews is renamed, as are the ambiguous review
fields; the names are in `MIGRATION.md` and the removed ones have no aliases.
