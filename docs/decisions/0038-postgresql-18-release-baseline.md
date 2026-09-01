# ADR 0038: PostgreSQL 18 is the qualified release baseline

## Status

Accepted (amended 2026-08-31 — see the update below)

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

## Update — 2026-08-31 (PR #1673)

One point of the decision is reversed before it ships in a release: the self-hosted Compose volume
keeps its stable `postgresql-data` name (`postgres-data` in the preview stack) instead of moving to
a version-suffixed `postgresql-data-v18`. Everything else above stands — PostgreSQL 18 as the
qualified baseline, the `/var/lib/postgresql` mount, and the dump-and-restore upgrade.

What the rename bought was a retained PostgreSQL 17 cluster for rollback. What it cost:

- The residual risk flagged at review — with a renamed volume, an operator who upgrades without
  migrating gets a stack that comes up **healthy but empty**, because Compose silently creates the
  new volume. With the stable name the same mistake fails loudly: the 18+ image's entrypoint
  detects the PostgreSQL 17 data in the volume and refuses to start (major-version data directories
  are incompatible with the server), so nothing is silently lost and the operator is pointed back
  at the documented migration.
- Stable volume names are the Compose norm; a version-suffixed name forces a rename — and a fresh
  round of this reasoning — at every future major.

Rollback safety now rests on the verified dump rather than a retained volume:
`docs/admin/backup-restore.mdx` § "PostgreSQL 17 to 18" requires `pg_restore --list` and an
off-host copy before the volume is removed. `scripts/postgres-major-upgrade-test.ts` rehearses the
stable-name flow (dump → destroy volume → fresh PostgreSQL 18 initialization → restore) and proves
the loud-refusal property.
