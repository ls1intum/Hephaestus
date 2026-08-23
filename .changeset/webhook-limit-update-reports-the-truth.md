---
"hephaestus": patch
---

A webhook stream limit change that takes longer than the broker's reply now says what actually happened. Bounding a stream that has outgrown its new limit deletes the excess before the broker answers, so on a large stream the wait is proportional to the data being shed — long enough that startup gave up and reported the stream "left at its live configuration" while the broker went on to apply the change. An operator reading that was told the opposite of the truth: that nothing had happened, when tens of gigabytes had just been deleted. Startup now waits long enough for the change to land, and if it still cannot get an answer it reports the limits the stream actually has rather than assuming its own update failed.

**Operators:** the new `HEPHAESTUS_WEBHOOK_STREAM_LIMIT_UPDATE_TIMEOUT` defaults to `5m` and applies only at startup. Raise it if a stream is large enough that shedding the excess takes longer than that; it is deliberately separate from the health-check timeouts, which must stay short to be worth alerting on.
