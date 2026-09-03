---
"hephaestus": patch
---

A fresh self-host install now starts instead of crash-looping with
`hephaestus.security.prior-credential-encryption-key and prior-credential-encryption-key-version must
be configured together`. The stack passes both credential-rotation variables through empty when you
have not set them, and Hephaestus was reading one empty value as a half-finished key rotation; an
empty value now means what you meant by it — not configured. Finishing a rotation by clearing the
prior key and its version works the same way. Setting only one of the two is still refused at
startup, so a rotation cannot half-apply and leave stored credentials unreadable.
