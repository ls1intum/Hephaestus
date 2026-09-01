---
"hephaestus": minor
---

Production startup now reports all catalogued production configuration problems together without
exposing configured values, and instance administrators can inspect redacted deployment and runtime
readiness facts through the API. New self-hosted installations generate and preserve internal secrets
with `setup.sh`. **Operators:** validate production settings against the configuration readiness guide
before upgrading; the process now refuses to start when a catalogued required setting is missing or
invalid.
