---
"hephaestus": minor
---

Staging follows the default branch, so a merge reaches it in minutes rather than waiting for someone
to cut a version. Its channel names the commit and the images to run, each pinned by digest and each
required to carry this repository's build provenance, so nothing runs there that a release would not
have been allowed to run.

Releases are unchanged and remain how production is promoted. A release does not touch staging: it
re-tags the images of the commit staging is already running, so there is nothing left to rehearse.
To hold an environment on what it has, freeze its channel.
