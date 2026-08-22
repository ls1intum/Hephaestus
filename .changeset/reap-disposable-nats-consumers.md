---
"hephaestus": minor
---

Deployments that are deleted rather than shut down can now hand their NATS consumers back automatically. Set `HEPHAESTUS_INTEGRATION_CONSUMER_INACTIVE_THRESHOLD` (for example `72h`) on any stack that shares a NATS server but is disposable — a pull request preview, a throwaway test environment — and its consumers are removed once nothing has been bound for that long, instead of accumulating on the shared stream one generation per deleted stack. Left unset, which is the default and what a long-lived deployment wants, consumers keep their position across restarts exactly as before.
