---
"hephaestus": patch
---

Erasing an account now reliably removes that person from the configuration audit trail on instances
whose database login is not a superuser. The audit trail is deliberately append-only, and the one
change it has always been meant to accept is an erasure blanking the two columns that name a person.
A database-level restriction shipped alongside that rule was broader than the rule itself and would
have rejected the erasure outright, leaving the erased account still named in the trail. The
append-only guarantee is unchanged — the trail still refuses ordinary edits, deletes, and truncation.
