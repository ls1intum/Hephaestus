---
"hephaestus": patch
---

The worker in the Compose stack accepts the optional `BPL_JVM_HEAD_ROOM` setting. Use it to keep a percentage of the worker's container memory out of the Java memory that the image calculates at startup. If you do not set it, nothing changes.
