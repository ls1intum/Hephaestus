---
---

No user-facing or operator-facing effect. A cancelled CI run left the "All CI Passed" status stuck
rather than reporting the cancellation, because the gate posted the run's outcome under a name
GitHub's commit-status API rejects. Repository tooling only.
