---
---

No user-facing or operator-facing effect. The agent sandbox now runs on Bun 1.4, which replaces the
runtime's core and reports itself as a newer Node.js. The Pi SDK, its native FFI dependency, the
practice and mentor runners and the whole agent test suite were re-verified against it inside the
image the sandbox actually ships. Practice reviews are off by default and the mentor is opt-in per
workspace, so no instance running the shipped defaults behaves differently after this change.
