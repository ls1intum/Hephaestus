---
"hephaestus": minor
---

**Operators:** PostgreSQL 18 is now the bundled and qualified database target. Before upgrading a stack whose volume was initialized by PostgreSQL 17, follow the documented dump-and-restore procedure; do not attach the old data directory to the new container.
