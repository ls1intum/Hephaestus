---
"hephaestus": patch
---

Restarting the application server during a scheduled sync now asks that sync to stop as shutdown begins, while open requests and live updates still drain. Before, an open live-update stream could delay the request until the request drain timed out, which left the sync less of the shutdown time to stop and record that it was cancelled.
