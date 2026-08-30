# ADR 0038: PostgreSQL 18 is the qualified release baseline

## Status

Accepted

## Context

[ADR 0018](0018-pg-partman-for-auth-event-partitioning.md) held PostgreSQL 17 while introducing
pg_partman so the changes could be evaluated separately. The PostgreSQL 18 image persists its
versioned `PGDATA` below `/var/lib/postgresql`; PostgreSQL 17 data directories are not binary
compatible with PostgreSQL 18. pg_partman 5.5 includes the security fixes documented in its release
notes. Maintenance remains application-scheduled as decided in ADR 0018.

## Decision

- Ship PostgreSQL 18 with pg_partman 5.5 as the release, local-development, preview, and CI target.
- Continue to support PostgreSQL 17 as the minimum for externally operated installations. The
  repository-managed Compose topology is qualified on PostgreSQL 18.
- Mount PostgreSQL 18 persistence at `/var/lib/postgresql`, as required by the official image.
- Use a new self-hosted Compose volume for PostgreSQL 18 so rollback retains the PostgreSQL 17 cluster.
- Upgrade self-hosted installations by custom-format logical dump and transactional restore into an
  empty PostgreSQL 18 cluster. Do not attempt an in-place container image swap.

## Consequences

Operators with PostgreSQL 17 volumes must complete the documented migration before starting
PostgreSQL 18. Preview environments create a fresh PostgreSQL 18 cluster.

## Sources

- [PostgreSQL versioning policy](https://www.postgresql.org/support/versioning/)
- [PostgreSQL major-upgrade documentation](https://www.postgresql.org/docs/18/upgrading.html)
- [Official image `PGDATA` contract](https://github.com/docker-library/docs/blob/master/postgres/README.md#pgdata)
- [pg_partman 5.5 release notes](https://github.com/pgpartman/pg_partman/releases/tag/v5.5.0)
