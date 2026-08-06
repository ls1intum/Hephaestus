---
"hephaestus": minor
---

Reviewed work is named the same way everywhere. A practice, a review run and a recorded observation
all now identify what was reviewed as `scm.pull_request`, `scm.issue` or `chat.conversation_thread`,
instead of two internal vocabularies that had already drifted apart — a chat thread was called one
thing where reviews are stored and another where they are run. The bundled practice catalog, the API
and the admin screens use the new names, and existing practices, reviews and observations are moved
across on upgrade.

**Operators:** the upgrade rewrites the names in place and needs no action, but it is a one-way
change: rolling the release back requires rolling the database change back with it. Two effects are
worth knowing about while the first reviews run afterwards. A piece of feedback that was already
posted on an open pull request or thread may be posted once more rather than updated in place, since
what ties a re-review to an earlier one is derived from the old name. And practice review rules are
re-fingerprinted on the first start after the upgrade, so a practice can briefly show as differing
from its Hephaestus default until that finishes.
