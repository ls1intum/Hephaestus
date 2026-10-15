---
"hephaestus": patch
---

Release publication can resume failed image tag promotion after the release becomes immutable. Retrying verifies the published evidence and host qualification records without replacing release assets.

Only the newest published stable release can move image tags. An older retry cannot replace the tags of a newer release.
