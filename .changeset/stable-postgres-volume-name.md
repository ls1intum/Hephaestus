---
"hephaestus": minor
---

**Operators:** the PostgreSQL Compose volume keeps its stable `postgresql-data` name across the 17 → 18 upgrade instead of moving to a version-suffixed `postgresql-data-v18`. This corrects the PostgreSQL 18 qualification shipping in this same release, so no deployed instance ever sees the `-v18` name. The upgrade is dump, remove the volume, restore into the freshly initialized PostgreSQL 18 cluster; a PostgreSQL 18 container started against un-migrated PostgreSQL 17 data refuses to start instead of coming up empty.
