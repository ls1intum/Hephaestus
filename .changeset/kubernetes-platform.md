---
"hephaestus": minor
---

You can now try Hephaestus on Kubernetes with the Helm chart that ships with each release. The chart runs the server, the webhook receiver and the webapp from the images in the signed release lock, behind a Gateway that your platform provides. Every role connects to PostgreSQL over TLS and verifies the server certificate. An optional worker runs each practice review as a Kubernetes Job, and the Heph host and Heph's tool sandboxes as Pods, in a sandbox namespace that only reaches the worker. Before it runs any sandbox, the worker checks that your cluster enforces restricted Pod security, the network policies and a per-Pod process limit, and it stops if the check fails. A gVisor runtime class and the shipped seccomp profile are optional. Docker Compose remains the supported installation, and Docker workers behave as before.
