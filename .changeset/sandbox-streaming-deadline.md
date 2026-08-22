---
---

No user-facing or operator-facing effect. Waiting for an agent container to exit shared a Docker
connection with ordinary requests, and that connection carried a socket timeout — so a review still
working after thirty minutes had its wait torn down and was reported as failed. The wait now runs on
its own connection whose timeout sits above the longest run a sandbox is allowed, which cannot cut a
legitimate wait short but still reclaims the connection if the daemon stops answering. None of this
reached a release: agent sandboxes have never shipped, so there is no version an operator can be
upgrading from where a long review failed this way.
