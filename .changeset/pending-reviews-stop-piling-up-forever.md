---
"hephaestus": patch
---

Work that could not be reviewed when it arrived no longer stays queued forever. A review blocked by
something an operator can lift — an exhausted budget, a paused workspace, a practice turned off — is
retried on a schedule and, if the blocker never clears, is finally retired and marked as such. It
previously kept its place in the queue indefinitely: the retry deadline could never be reached, so the
trace showed "Queued for review" for work nothing would ever review, and each of those items re-ran the
full review gate every hour for as long as the instance lived. Long-stuck items now also stop crowding
out newer ones.
