---
"hephaestus": patch
---

Internal only, with no user-visible change. A family of reviewable work can now declare, in one place, the signal raised when somebody asks for a review of it by hand, and the server refuses to start if that declaration cannot be recorded honestly — if two signals claim to be the request, if the request is keyed on anything that would make a second person's ask collide with the first, or if an integration claims one of its events raises a request only a person can raise.

The review gate understands such a request: it admits every practice the workspace measures on that kind of work, rather than looking for practices bound to a signal nothing binds to. No surface raises one yet, so nothing changes for operators or users until the endpoint that does lands.
