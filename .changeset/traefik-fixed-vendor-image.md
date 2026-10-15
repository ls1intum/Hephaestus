---
"hephaestus": patch
---

The bundled reverse proxy now uses Traefik 3.7.15, which fixes the Go HTTP/2, byte-range and TLS vulnerabilities tracked for the previous image. The temporary security exceptions for that image are removed.
