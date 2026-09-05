---
"hephaestus": patch
---

A login-provider change that the database refuses no longer appears in the audit viewer as if it had
succeeded. Moving a provider onto a base URL another provider already uses is rejected, and the
rejection now happens before the change is recorded, so the trail matches what the instance actually
has configured.
