---
"hephaestus": patch
---

An instance now sends `Strict-Transport-Security` whatever proxy sits in front of it. The header was
attached to the reverse proxy shipped with Hephaestus, so a deployment fronted by a different proxy —
a PaaS, or an existing ingress — served without it, and the omission was easy to miss because every
other security header comes from the responses themselves. Nothing changes for a deployment that uses
the bundled proxy.
