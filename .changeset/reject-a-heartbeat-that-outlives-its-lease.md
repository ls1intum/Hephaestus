---
"hephaestus": minor
---

Refuses to start a worker whose liveness heartbeat is slower than the lease it renews. Such a worker was judged dead while it was still running, so its in-flight reviews were requeued onto a sibling and the same work ran twice at double the model spend.

**Operators:** the shipped default needs no change. If you override `hephaestus.agent.heartbeat-interval`, it must now be at most 30s; a larger value fails startup with the limit in the message instead of silently duplicating work.
