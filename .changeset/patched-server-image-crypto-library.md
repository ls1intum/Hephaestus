---
"hephaestus": patch
---

The server image no longer ships a cryptography library with a known critical vulnerability, so it passes a high- and critical-severity image scan with nothing left to disposition. Deployments that talk to a TLS-protected Docker daemon keep working unchanged.
