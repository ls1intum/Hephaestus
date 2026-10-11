---
"hephaestus": patch
---

Release signing and verification use Cosign 3.1.3, which rejects legacy signature bundles that could bypass the required signer identity and issuer. Operators should update Cosign on deployment hosts to 3.1.3 or later.
