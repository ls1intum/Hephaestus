---
"hephaestus": minor
---

Staging now follows the default branch instead of waiting for a release, so a merge reaches it in
minutes rather than sitting undeployed until someone cuts a version. Its channel names the commit
and the images to run, each pinned by digest and each required to carry this repository's build
provenance, so nothing runs there that a release would not have been allowed to run.

Releases are unchanged and remain how production is promoted. A release no longer promotes staging:
it re-tags the images of a commit staging has already been running, so there is nothing left to
rehearse. To hold an environment on what it has, freeze its channel.
