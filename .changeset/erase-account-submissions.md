---
"hephaestus": minor
---

Deleting your account now also deletes the product feedback you sent and the survey answers or dismissals you gave. They used to survive account deletion: the deleted account is kept as an empty placeholder, so the database cleanup that should have removed them never ran.

**Operators:** the upgrade permanently deletes the product-feedback submissions and survey answers of accounts that were already deleted. It runs once, as part of the database migration, and cannot be undone — export them or take a database backup first if you need to keep them.
