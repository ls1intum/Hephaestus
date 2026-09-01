---
"hephaestus": minor
---

Moves the container images to `ghcr.io/hephaestus-build/<image>` after the repository's transfer to the hephaestus-build organization, dropping the redundant `hephaestus/` path segment. Releases published before the move keep their images and signatures at `ghcr.io/ls1intum/hephaestus/<image>`; upgrades, deploys, and signature verification select the right namespace and signing identity per release automatically.

**Operators:** From this release on, images pull from `ghcr.io/hephaestus-build/<image>`. Update any registry mirrors, egress allowlists, or hand-written image references (such as a pinned `HEPHAESTUS_AGENT_IMAGE_REFERENCE`); the standard install and upgrade flow needs no changes. Older releases remain valid at their original `ghcr.io/ls1intum/hephaestus/<image>` paths.
