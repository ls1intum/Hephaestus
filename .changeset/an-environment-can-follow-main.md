---
"hephaestus": minor
---

An environment can now follow the default branch instead of waiting for a release. Its channel names
a commit and the images to run, each pinned by digest and each required to carry GitHub's build
provenance for this repository, so nothing is trusted that a release would not have been. Releases
are unchanged and remain how production is promoted; an instance that follows a branch reports the
commit it is running as its version.
