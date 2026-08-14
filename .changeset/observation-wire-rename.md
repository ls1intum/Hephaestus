---
"hephaestus": minor
---

Practice review screens now say **observation** for a recorded review result and **feedback** for the guidance built from it. The words *finding* and *message* are gone from the reviews UI, from the workspace-admin practice review API, and from the vocabulary docs that had allowed them.

**Operators:** the admin API paths and fields under `/practices/reviews` changed with the wording — `.../reviews/findings` is now `.../reviews/observations`, and `findingId`, `findingCount` and `findings[]` are now `observationId`, `observationCount` and `observations[]`. Nothing else changes: this API is consumed only by the Hephaestus web app, which ships in the same release, so a normal upgrade needs no action. Anything you built directly against those endpoints needs the new names.
