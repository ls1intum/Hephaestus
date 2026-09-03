---
"hephaestus": patch
---

Dependencies in the agent image that runs practice reviews could be less than three days old,
escaping the release-age floor every other Hephaestus dependency is held to. The image now applies
that floor and verifies it while it builds.
