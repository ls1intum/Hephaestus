---
---

No user-facing or operator-facing effect. Type-checking the agent runtime for the first time
surfaced four defects that had been invisible to both test suites, all of them fixed here: the
mentor never received its own system prompt, so it answered in the voice of a generic coding
assistant; a practice review's result file could never be accepted, so admission and feedback
composition had been unreachable; the composer was told every observation was positive; and the
double-billing guard on token usage keyed on a field that does not exist, so it had never once
fired. None of this reached a release — agent sandboxes and the mentor have never shipped, so there
is no version an operator can be upgrading from where any of it was observable.
