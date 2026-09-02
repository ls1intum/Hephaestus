---
---

Re-enables the JPA-versus-database schema drift gate and realigns the schema it had stopped checking: nine foreign-key constraints are renamed to the scalar-key convention, the composite practice-workspace key on observations is mapped by the entity, and two Outline timestamp columns become NOT NULL after a backfill. Operators: the release applies one migration with no manual step; nothing changes for users.
