---
---

Internal: the generated API client now revives timestamp fields into real dates, which is what its
own types have always promised. Nothing an operator or user can observe changes — every screen
that reads a timestamp already normalised it defensively, or passed it to a formatter that accepted
either shape — so this removes a hazard rather than a symptom.

The hazard is worth naming, because it cost a crash once and was invisible to every gate: the code
generator emitted the conversion helpers but was never told to use them, so the types said "date"
while the client returned the raw string. Type-checking could not see the difference, and fixtures
built dates by hand, so the bug only ever appeared in front of a user. Both sides are now closed —
the client does the conversion, and a test calls the real client and fails if that wiring is ever
lost again.
