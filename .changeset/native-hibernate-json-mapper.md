---
"hephaestus": minor
---

Audit text and GitHub project options now retain their supplied text. Typed JSON data uses the application's configured Jackson 3 mapper.

**Operators:** Stop all replicas of the server, worker, and webhook runtime roles before this database migration. Verify a database backup first. Restart all roles on the new release together. Previously structured audit values become JSON-text Strings in person-data exports. Downgrades require restoration of the verified backup. Follow the native Hibernate JSON cutover procedure in the operator documentation.
