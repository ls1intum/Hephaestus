---
"hephaestus": patch
---

Clears 22 high and critical vulnerabilities from the PostgreSQL image. The image no longer carries the bundled Go helper that dropped privileges at start-up — the same step now uses a tool the operating-system updates keep patched, so the vulnerabilities cannot come back with the next rebuild. The database initialises, restarts and runs exactly as before, and no configuration changes.
