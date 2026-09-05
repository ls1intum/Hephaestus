---
"hephaestus": patch
---

Issue metadata edits now wait briefly for triage to settle before starting a practice review, instead of spending a review on every intermediate snapshot. Pending updates survive server restarts, duplicate deliveries do not extend the delay, and queued reviews refuse metadata that changed after admission. Review activity explains coalesced updates, and operators can count deferred and duplicate occasions.

Previously queued issue-update reviews without a recorded admission revision stop safely rather than guessing which metadata they were meant to review. A manual review can be requested if needed.

A review that was rolled back is no longer written to the logs as if it had started.
