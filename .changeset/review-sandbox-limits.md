---
"hephaestus": minor
---

Review sandboxes now use the limits that `SANDBOX_MEMORY_BYTES`, `SANDBOX_CPUS` and `SANDBOX_PIDS_LIMIT` set. Before, every review got 4 GiB of memory, 2 CPUs and 512 processes, whatever these settings said. The new defaults are 2 GiB, 1 CPU and 256 processes, based on the measured use of a review. Thus, the same host can run more reviews at once. If your reviews cover large repositories, or you want the earlier memory limit, set `SANDBOX_MEMORY_BYTES=4294967296`. The Compose file now also passes `SANDBOX_PIDS_LIMIT` to the worker.
