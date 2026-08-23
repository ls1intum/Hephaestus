---
"hephaestus": minor
---

Practice reviews and mentor conversations now run in the agent sandbox image built from the same
commit as the application server. A deployment that tracked `main` previously fell back to the newest
released sandbox image, which pairs a server with a sandbox nobody built it against — reviews and
mentor turns then failed inside the container with nothing explaining why. The server now reports at
startup when the sandbox image cannot run it, naming both versions.

**Operators:** the sandbox image now follows `IMAGE_TAG`, so a tag that moves between builds refuses
to start — `IMAGE_TAG=latest`, which earlier example configuration shipped, and equally a partial
version such as `0.73`, which every patch release moves. The same goes for setting
`HEPHAESTUS_AGENT_IMAGE_REFERENCE` to one. Set `IMAGE_TAG` to a full release version or a commit SHA,
and remove or digest-pin the reference override, before upgrading. This applies even with the agent
disabled. Release deployments that changed neither, and take the signed digest pin, are unaffected.
See MIGRATION.md.
