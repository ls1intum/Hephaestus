---
"hephaestus": patch
---

A review that continues its feedback composition in a new attempt no longer fails at once. Before, the new attempt stopped with exit 42 before any model call, so its feedback was never composed. When the agent cannot use its task, the server log now says that the task was missing, unreadable, from another version or invalid. It no longer always tells you to rebuild the agent image.
