---
"hephaestus": patch
---

Turning on practice review no longer leaves the instance reporting itself as out of service. The worker that runs reviews was never given the git-checkout setting the rest of the deployment gets, so enabling reviews produced a deployment that reported `GIT_CHECKOUT_DISABLED` and reviewed nothing. The worker now reads the same setting as the application server.
