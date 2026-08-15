---
"hephaestus": minor
---

The limits on reviewing work that already existed are now yours to set. A backfill campaign's ceilings
— the longest window it may cover and the largest number of items it may be confirmed for — and the
batch size and pricing window it works from can be configured per deployment instead of being fixed at
the values the product shipped with. The same is true of the pending-review queue: how long a review
waits before it is offered again, how long it keeps waiting before it is retired, and how many are
re-offered per pass.

Defaults are unchanged, so an upgrade behaves exactly as before.

**Operators:** all optional — `PRACTICE_REVIEW_BACKFILL_MAX_WINDOW` (default `400d`),
`PRACTICE_REVIEW_BACKFILL_MAX_ARTIFACTS` (default `5000`), `PRACTICE_REVIEW_BACKFILL_BATCH_SIZE`
(default `25`), `PRACTICE_REVIEW_BACKFILL_COST_HISTORY_WINDOW` (default `90d`),
`SIGNAL_LEDGER_PENDING_RETRY_AFTER` (default `1h`), `SIGNAL_LEDGER_PENDING_LAPSE_AFTER` (default `7d`),
`SIGNAL_LEDGER_SWEEP_BATCH_SIZE` (default `200`).

Two smaller changes for anyone driving the API directly: creating a backfill campaign or a sweep
schedule now returns the address of what it created, and being told "this workspace already sweeps that
kind of work" is now its own kind of conflict rather than being reported as a campaign conflict.
