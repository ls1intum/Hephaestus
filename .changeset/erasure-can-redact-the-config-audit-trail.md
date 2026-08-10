---
---

No user-facing or operator-facing effect. Erasing an account has to blank the two columns that name a
person in the configuration audit trail, and the database-level rule protecting that trail was broader
than the append-only guarantee it enforces, so it would have rejected the erasure. Both the trail and
the rule are new in this same release: there is no version an operator can be upgrading from where an
erased account stayed named in it.
