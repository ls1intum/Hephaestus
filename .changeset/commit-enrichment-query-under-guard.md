---
---

No user-facing or operator-facing effect. The last GitHub request still assembled entirely in code is
now checked against GitHub's published schema like every other one, and the way shared query fragments
are loaded no longer lets a comment in one file reach into another. With the files that actually
ship, no request was ever malformed, so nothing an operator or a user could observe changes — the
point is that a class of error which breaks GitHub sync outright is now caught by the test suite
before it can reach a release.
