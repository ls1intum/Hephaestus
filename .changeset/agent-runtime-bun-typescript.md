---
---

No user-facing or operator-facing effect. The agent sandbox now runs entirely on Bun: the runner is
TypeScript executed directly, and the image no longer contains Node.js or npm, which removes a
dependency-install code-execution path and takes the built image from 866 MB to 657 MB. The runner is
now type-checked, linted and formatted alongside the rest of the repository. Practice reviews are off
by default and the mentor is opt-in per workspace, so no instance running the shipped defaults
behaves differently after this change.
