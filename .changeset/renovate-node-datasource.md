---
---

No release note: the one shipped file this pull request touches carries a comment change only — the
agent image records which list of Node.js releases its pin is read from, so the three places that
pin the same Node.js version are read from the same list. The image is built from the same version
it was built from before.
