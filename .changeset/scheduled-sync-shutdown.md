---
"hephaestus": patch
---

Restarting the application server during a scheduled sync now gives that sync up to the shutdown timeout (`SHUTDOWN_TIMEOUT`) to stop before the database closes. A sync that reaches its next checkpoint in that time records that it was cancelled, and the scheduled run releases its lock only after its workspace syncs have stopped. A provider error during that time is recorded as a failure. A sync that does not stop in time stays marked as running until it is reaped as abandoned, and its lock expires on its own.
