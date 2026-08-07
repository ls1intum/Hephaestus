---
"hephaestus": minor
---

A practice can now be turned down without being turned off. Instead of one switch, each practice in a
workspace sits at one of four loudness tiers:

- **Off** — not reviewed at all.
- **Measure** — reviewed and recorded, and nobody is told anything.
- **Coach** — also raised in the developer's mentor chat, never on the pull request itself.
- **Engage** — also posted on the pull request or issue. This is the default.

Measure is the tier this exists for. Until now, the only remedy for a practice that was too noisy was
to stop using it in new reviews, which also stopped measuring it and left a hole in that team's
history exactly where they had a problem worth watching. Every tier above Off still runs the review
and still records every observation; they differ only in how far the result travels.

Coach reaches the mentor conversation and nothing else. Feedback held back by a tier is recorded as
withheld rather than dropped, so a review that deliberately said nothing still reads differently from
one that found nothing.

**Operators:** if you drive practices through the REST API, the endpoint is now
`PATCH /workspaces/{slug}/practices/{practiceSlug}/review-tier` with `{"reviewTier": "..."}`, and the
practice payload carries `reviewTier` instead of `usedInNewReviews`. See `MIGRATION.md`. Nothing to do
if you use the web interface: your existing choices carry over unchanged.
