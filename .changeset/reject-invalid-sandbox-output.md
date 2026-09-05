---
"hephaestus": patch
---

Sandbox executions no longer succeed with empty or partial results when output collection fails. Malformed, oversized, or unsupported output archives fail collection, and rejected transfers are aborted without downloading the remaining data.

Host-directory inputs now omit symbolic links instead of copying their target files.
