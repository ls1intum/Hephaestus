---
"hephaestus": patch
---

A practice can now require that a source is not empty. A pull request whose diff turns out to contain no changes is skipped instead of being reviewed from its title and description alone.

A Slack thread in a channel whose consent is paused or withdrawn is now reported as withheld rather than as an empty conversation, so a developer is never reviewed on messages Hephaestus was not permitted to read.

A review-comment, review-thread, or workspace-inventory query that cannot be run because the job did not identify the work item is now recorded as a collection error. It was previously recorded as an empty result, which reads as an established fact about the work.
