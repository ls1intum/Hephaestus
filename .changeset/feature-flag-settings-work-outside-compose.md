---
"hephaestus": patch
---

`GITLAB_WORKSPACE_CREATION` and `PRACTICE_REVIEW_FOR_ALL` now do what the documentation says
wherever you set them. Both were names the shipped compose files translated into something else, so
on any deployment that does not use those files — Kubernetes, a plain JVM, your own compose — setting
them did nothing at all and reported nothing. They are now settings the application reads directly.
The longer `HEPHAESTUS_FEATURES_FLAGS_…` spellings keep working and still take precedence, so
nothing has to change.
