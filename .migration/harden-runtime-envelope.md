#### 🔴 The runtime envelope is hardened: NATS requires credentials, remote databases require TLS

**Affected**: every reference and self-hosted deployment. Deployments using a remote (non-Compose)
PostgreSQL host are additionally affected by the TLS requirement.

**Before**: the bundled NATS broker accepted unauthenticated connections (mitigated only by its
loopback bind), a remote `DATABASE_URL` with `sslmode=disable` connected silently, and each server
role opened up to 30 database connections.

**After**: the broker, publisher, and consumer all require the same credentials and the stack
refuses to start without them; in production a remote PostgreSQL host without `sslmode=require`
(or stronger) aborts startup; each server role's connection pool defaults to 20.

**Migration**: before deploying, set `NATS_USERNAME` and `NATS_PASSWORD` in your `.env` to freshly
generated random values (do not reuse an application key); deployments driven by the deploy
workflow need the same pair as environment secrets. If your database host is remote, add
`sslmode=require`, `verify-ca`, or `verify-full` to `DATABASE_URL` — only set
`HEPHAESTUS_DATABASE_ALLOW_INSECURE_REMOTE=true` after explicitly accepting plaintext transport.
Optional tuning: `HIKARI_MAXIMUM_POOL_SIZE` restores a larger pool, and the new
`APPLICATION_SERVER_CPUS`/`APPLICATION_SERVER_PIDS_LIMIT` (plus the worker and webhook variants)
adjust the container ceilings.
