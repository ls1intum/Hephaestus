---
"hephaestus": patch
---

A practice can now require that a source actually holds something, so a pull request whose diff turns out to be empty is skipped instead of being reviewed from its title alone.

A Slack thread in a paused or revoked channel is now reported as withheld rather than as a conversation nobody took part in, so a developer is never assessed on messages Hephaestus was not permitted to read.

Review evidence that could not be looked up — a review-comment, thread, or workspace-inventory query that arrived without the item to look up — is now recorded as a collection error instead of an empty result that reads as an established fact.

The instance catalog now spells out whether a pending Hephaestus update changes wording or changes what the AI reviews, rather than signalling it with colour alone.
