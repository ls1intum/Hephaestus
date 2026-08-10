---
"hephaestus": patch
---

A review can now tell the difference between evidence it looked at and found nothing in, and evidence
it never got to look at. Sources that turned up empty — a pull request nobody commented on, a project
with no other tracked work, a change that links no documentation — used to be left out of the
review's workspace entirely, which looks exactly like a source that failed to collect. They are now
always present and simply empty, which removes a class of findings that were confidently right or
confidently wrong for the same reason.

The trace says one thing about a source rather than three that contradict each other: a source
nothing captured is reported as not captured, and "captured only in part" and "captured empty" appear
only where a capture actually happened.

Where a source must not be empty, that is now enforced: a pull request whose diff turns out to
contain no changes is skipped rather than reviewed from its title and description alone. A Slack
thread in a channel whose consent is paused or withdrawn is reported as withheld rather than as an
empty conversation, so a developer is never reviewed on messages Hephaestus was not permitted to
read. And a query that could not be run at all is recorded as a collection error rather than as an
empty result, which would read as an established fact about the work.
