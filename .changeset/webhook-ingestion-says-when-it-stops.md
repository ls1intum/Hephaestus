---
"hephaestus": minor
---

An ingestion outage is no longer silent. The readiness check reported only that the process had started: the checks that know whether webhooks can be received, whether the message consumer is connected and whether reviews can run were meant to be part of it and never were. A deployment whose message broker had stopped accepting writes kept answering healthy while it dropped every delivery from GitHub, GitLab, Slack and Outline. Readiness now reports all of them, which is what makes an alert on it possible.

The server also counts webhooks that were lost rather than how close a stream is to being full: if a message is deleted before the consumer that needed it has read it, that is recorded, named, and logged as an error — the one thing nobody can recover from afterwards.

**Operators:** readiness now fails while the message broker is unreachable, which on a container that also serves the app takes it out of load-balancer rotation until the broker recovers. If you treated readiness as a liveness signal, it now reports operational dependencies too. Alert on `webhook.stream.unacknowledged.deletions` — any increase is webhook data that is gone for good — and on `webhook.stream.poll.age` beside it, because a check that cannot reach the broker reports no loss and no loss the same way.
