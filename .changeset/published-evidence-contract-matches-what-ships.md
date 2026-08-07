---
---

No user-facing or operator-facing effect. The machine-readable schemas published alongside the
evidence contract had fallen behind the code they describe — one broken internal reference, and a
policy shape from two refactors ago — and the build-time check that should have caught it had been
written against the same retired vocabulary, so it crashed instead of failing. The schemas now match
what ships and the check verifies them again; nothing an operator or a user interacts with changes.
