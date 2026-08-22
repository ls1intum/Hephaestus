---
---

No user-facing or operator-facing effect. The agent sandbox now runs entirely on Bun: the runner is
TypeScript executed directly, and the image no longer contains Node.js or npm, which removes a class
of dependency-install code execution and takes roughly 200 MB off every agent image pull. The runtime
is also type-checked, linted and formatted alongside the rest of the repository for the first time.
Agent sandboxes have never shipped, so there is no version an operator can be upgrading from.
