#### 🔴 The PostgreSQL upgrade keeps the stable volume name — rely on the verified dump, not a retained volume

**Affected**: operators performing the PostgreSQL 17 → 18 migration this release requires. This
entry corrects the "Restore PostgreSQL 17 data into PostgreSQL 18" entry above, which predates it.

**Before**: this release was drafted to start PostgreSQL 18 on a new `postgresql-data-v18` volume,
leaving the PostgreSQL 17 volume in place as the rollback path.

**After**: the Compose volume keeps its stable `postgresql-data` name. The documented upgrade
verifies the dump, removes the PostgreSQL 17 volume, and lets PostgreSQL 18 initialize a fresh
cluster under the same name. Starting the new release without migrating is safe: a PostgreSQL 18
container attached to PostgreSQL 17 data refuses to start rather than coming up healthy and empty.

**Migration**: follow the current
[Backup & Restore](https://docs.hephaestus.build/admin/backup-restore#postgresql-17-to-18)
procedure. Where the entry above says to keep the PostgreSQL 17 volume until acceptance checks
pass, keep the verified dump (with an off-host copy) instead — the old volume is removed during
the upgrade, so the dump is the rollback artifact.
