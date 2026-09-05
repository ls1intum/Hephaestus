#### 🔴 Product feedback and survey answers of already-deleted accounts are removed during the upgrade

**Affected**: every deployment where someone deleted their account after sending product feedback or
answering a survey.

**Before**: account deletion left those submissions in the database. A deleted account is kept as an
empty placeholder rather than removed, so the cascade that would have deleted its submissions never
fired, and the free text and answers stayed indefinitely.

**After**: account deletion removes them, and the database migration in this release removes the ones
earlier releases left behind. Both deletions are permanent.

**Migration**: nothing to configure. If you have to keep those submissions — a legal hold, or
reporting on product feedback — export them or take a database backup before you deploy this release.
