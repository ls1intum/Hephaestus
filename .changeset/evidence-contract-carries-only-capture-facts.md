---
"hephaestus": minor
---

The practice editor now shows how a practice's evidence requirements have actually turned out: how many of the recent reviews they let through, and which source skipped the rest. Requirements that quietly skip most reviews used to look identical to ones that never skip.

Two sources stop overstating what they hold. Linked work items and Outline documents are both found by heuristics that cannot establish they found everything, so neither is reported as fully captured any more, and a practice can no longer require that of them.

**Operators:** the review-evidence manifest recorded with each review no longer carries `queryScope`, `completenessBasis`, `representationFidelity` or `viewTransformations`. The first three restated values from the source catalog the manifest already pins by digest; the last was never populated. Anything reading those fields out of a stored manifest should read the pinned catalog instead.
