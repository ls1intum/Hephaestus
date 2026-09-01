---
"hephaestus": minor
---

Operators can rotate stored integration credential encryption keys without disconnecting configured providers. **Operators:** set `HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY` before upgrading; the supported self-host installer derives the initial value automatically. The optional `HEPHAESTUS_SECURITY_CREDENTIAL_ENCRYPTION_KEY_VERSION`, `HEPHAESTUS_SECURITY_PRIOR_CREDENTIAL_ENCRYPTION_KEY`, `HEPHAESTUS_SECURITY_PRIOR_CREDENTIAL_ENCRYPTION_KEY_VERSION`, and `HEPHAESTUS_SECURITY_CREDENTIAL_ROTATION_ENABLED` variables drive the documented online rotation procedure.
