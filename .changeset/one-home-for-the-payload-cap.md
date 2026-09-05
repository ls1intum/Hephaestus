---
---

No release note: the webhook ingest surface and the sandbox gateway refuse exactly the requests they
refused before, with the same status codes and the same per-provider counters. They now enforce the
cap with one filter instead of two copies of it.
