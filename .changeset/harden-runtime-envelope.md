---
"hephaestus": minor
---

Hardens the reference deployment with HTTPS security headers, a TLS floor, a request-size ceiling, shared rate limits for costly operations, authenticated internal messaging, and container resource limits.

**Operators:** Set the new required `NATS_USERNAME` and `NATS_PASSWORD` variables. Optional `*_CPUS` and `*_PIDS_LIMIT` variables tune container ceilings. Remote databases require TLS unless `HEPHAESTUS_DATABASE_ALLOW_INSECURE_REMOTE=true` explicitly accepts plaintext transport. Each server role's database pool now defaults to 20 connections instead of 30; the optional `HIKARI_MAXIMUM_POOL_SIZE` variable tunes it.
