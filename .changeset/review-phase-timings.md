---
"hephaestus": patch
---

Review details now separate the wait before an attempt from preparation and execution. For retried reviews, the request-to-attempt span includes earlier attempts and retry waits. Active phases show "In progress", and missing phase boundaries show "Not recorded".
