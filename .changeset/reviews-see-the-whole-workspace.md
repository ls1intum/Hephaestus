---
"hephaestus": minor
---

Automated practice reviews now see everything Hephaestus has collected about the work under review,
not a subset chosen ahead of time. Until now each review was handed only the sources the practices in
scope had declared, which meant most reviews never saw the rest of the project's issues and pull
requests at all, and only some saw the repository, the wiki documents, or the conversation on the
pull request. Reviews can now read all of it and cite any of it, so judgements that depend on
context outside the change — whether the work is already tracked elsewhere, whether a linked design
doc says what the change claims, what the code a changed line calls into actually does — stop coming
back as "not applicable" for want of the evidence.

This changes nothing about what is collected or kept: every one of these sources was already gathered
and stored for every review; the cut only decided what the reviewing model was shown. Sources still
require an unexpired use decision before they are read, a source with no collector in a deployment is
reported as such rather than silently missing, and a practice whose required evidence did not arrive
is still refused rather than reviewed on a guess.
