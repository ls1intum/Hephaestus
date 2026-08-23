---
"hephaestus": minor
---

A deployment's message-queue consumers no longer outlive the deployment itself. Consumers were kept forever, and nothing else removes one, so every stack that shared a broker and was deleted rather than shut down — a pull request preview, a throwaway test environment — left its consumers, and backlogs that would never drain, behind for good. They now expire on their own once nothing has been connected to them for long enough.

**Operators:** `HEPHAESTUS_INTEGRATION_CONSUMER_INACTIVE_THRESHOLD` defaults to `30d`, where it previously meant never. It measures time with nothing connected, not time without traffic — a running deployment resets it continuously even while its queues are silent, so no restart, deploy or incident reaches it. Set something far shorter, such as `72h`, on any disposable stack that shares a broker. If your deployment may be offline for longer than the threshold and must resume exactly where it left off, set `0s` to switch expiry off. Values between `0s` and `1h` are now rejected at startup rather than quietly expiring a consumer across an ordinary restart.
