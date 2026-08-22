---
---

No user-facing or operator-facing effect. The agent runtime — the practice precompute scripts and
the runner's own tests — is now type-checked and tested in CI on the Bun the sandbox actually ships,
and the checks run whenever that code changes rather than only when the application server does.
Build tooling only; nothing an operator or a user can observe changes.
