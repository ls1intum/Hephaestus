---
"hephaestus": minor
---

The directory Hephaestus keeps its working copies in is now named by `HEPHAESTUS_FABRIC_ROOT`. It has
held more than repository clones for several releases — cached review evidence and per-job manifests
sit beside them — and it is now named for what it is rather than for the one thing it started as.

**Operators:** if you set `GIT_STORAGE_PATH`, set `HEPHAESTUS_FABRIC_ROOT` to the same value before
starting the new version, then remove the old one. `GIT_STORAGE_PATH` is no longer read and there is
no alias, so an instance that keeps it does not fail — it silently falls back to `/data/git-repos`
and writes everything to a directory you did not choose. Deployments that use the shipped Compose
files unchanged are unaffected; those files already pinned this path and now pass the new name for
the same directory. See `MIGRATION.md`.
