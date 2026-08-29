---
"hephaestus": patch
---

The webapp image is now built from a digest-pinned nginx base without an ad-hoc package-upgrade layer, so the image you deploy matches its published SBOM and signed provenance; base updates arrive as reviewed dependency bumps instead of changing silently at build time.
