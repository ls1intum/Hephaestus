---
"hephaestus": minor
---

Practice authoring is now framed as AI-supported practice mentoring. An author states one observable
habit and then chooses how it is supported: **AI-supported mentoring**, **Human review needed**, or
**Guidance only**. That choice governs only what Hephaestus may review; it never limits what a
developer, a peer or a human mentor can observe.

Review timing and evidence are now stated per occasion, so a practice can ask for different evidence
when work is opened than when somebody asks for a review by hand, with a recommended timing and
evidence set covering the common path. How completely a source must be captured is fixed by the
source itself and shown rather than chosen, so two practices reading the same source can no longer
disagree about what counts as having read it.

Required evidence that is missing, that could only be captured in part, or that turned out to be
empty makes Hephaestus skip the practice and say which, instead of guessing from what it had.

Practice review also works on a default install now: the pull requests and issues of a connected
workspace need no extra configuration.

**Operators:** if Outline is enabled, set the same `HEPHAESTUS_INTEGRATION_OUTLINE_ALLOWED_ORIGINS`
value on the server, worker and webhook roles, then restart all three. An empty list blocks Outline
connections, sync, webhook collection, evidence projection and identity linking. See `MIGRATION.md`.

**API clients:** the AI purpose that runs practice reviews is renamed, as are the ambiguous review
fields; the names are in `MIGRATION.md` and the removed ones have no aliases.
